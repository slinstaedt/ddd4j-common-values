package org.ddd4j.infrastructure.channel.kafka;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ChannelListener;
import org.ddd4j.infrastructure.channel.ColdChannel;
import org.ddd4j.infrastructure.channel.HotChannel;
import org.ddd4j.infrastructure.channel.PartitionRebalanceListener;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.BlockingTask;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.Bytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.collection.Props;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public class KafkaCallback implements ColdChannel.Callback, HotChannel.Callback, BlockingTask {

	private class KafkaRebalanceListener implements ConsumerRebalanceListener {

		private final Consumer<byte[], byte[]> consumer;
		private final PartitionRebalanceListener listener;

		KafkaRebalanceListener(Consumer<byte[], byte[]> consumer, PartitionRebalanceListener listener) {
			this.consumer = Require.nonNull(consumer);
			this.listener = Require.nonNull(listener);
		}

		@Override
		public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
			consumer.seekToEnd(partitions);
			partitionsByTopic(partitions).forEach(listener::onPartitionsAssigned);
		}

		@Override
		public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
			partitionsByTopic(partitions).forEach(listener::onPartitionsRevoked);
		}

		private Map<ResourceDescriptor, int[]> partitionsByTopic(Collection<TopicPartition> partitions) {
			return partitions.stream().collect(groupingBy(tp -> new ResourceDescriptor(tp.topic()),
					mapping(TopicPartition::partition, collectingAndThen(toSet(), p -> p.stream().mapToInt(Integer::intValue).toArray()))));
		}
	}

	private static final ConsumerRecords<byte[], byte[]> EMPTY_RECORDS = ConsumerRecords.empty();

	static Committed<ReadBuffer, ReadBuffer> convert(ConsumerRecord<byte[], byte[]> record) {
		ReadBuffer key = Bytes.wrap(record.key()).buffered();
		ReadBuffer value = Bytes.wrap(record.value()).buffered();
		Revision actual = new Revision(record.partition(), record.offset());
		Revision next = actual.increment(1);
		ZonedDateTime timestamp = Instant.ofEpochMilli(record.timestamp()).atZone(ZoneOffset.UTC);
		// TODO deserialize header?
		Props header = new Props(Collections.emptyMap());// new Props(value);
		return new Committed<>(key, value, actual, next, timestamp, header);
	}

	private final Agent<Consumer<byte[], byte[]>> client;
	private final ChannelListener channelListener;
	private final KafkaRebalanceListener kafkaRebalanceListener;
	private final Map<String, Promise<Integer>> assignments;
	private final Rescheduler rescheduler;

	public KafkaCallback(Scheduler scheduler, Consumer<byte[], byte[]> consumer, ChannelListener channelListener,
			PartitionRebalanceListener rebalanceListener) {
		this.client = scheduler.createAgent(consumer);
		this.channelListener = Require.nonNull(channelListener);
		this.kafkaRebalanceListener = new KafkaRebalanceListener(consumer, rebalanceListener);
		this.assignments = new ConcurrentHashMap<>();
		this.rescheduler = scheduler.reschedulerFor(this);
	}

	void close() {
		client.perform(Consumer::close);
	}

	private void doAssignAndSeek(Consumer<?, ?> consumer, String topic, Revision revision) {
		TopicPartition partition = new TopicPartition(topic, revision.getPartition());
		Set<String> kafkaSubscriptions = consumer.subscription();
		if (kafkaSubscriptions.isEmpty()) {
			List<TopicPartition> partitions = new ArrayList<>(consumer.assignment());
			partitions.add(partition);
			consumer.assign(partitions);
		} else if (!kafkaSubscriptions.contains(topic)) {
			doSubscribe(consumer, topic);
		}
		consumer.seek(partition, revision.getOffset());
		rescheduler.doIfNecessary();
	}

	private int doFetchPartitionSize(Consumer<?, ?> consumer, String topic) {
		return consumer.partitionsFor(topic).size();
	}

	private int doSubscribe(Consumer<?, ?> consumer, String topic) {
		List<String> topics = new ArrayList<>(assignments.keySet());
		topics.add(topic);
		consumer.subscribe(topics, kafkaRebalanceListener);
		rescheduler.doIfNecessary();
		return doFetchPartitionSize(consumer, topic);
	}

	@Override
	public Promise<Trigger> scheduleWith(Executor executor, long timeout, TimeUnit unit) {
		return client.execute(c -> c.assignment().isEmpty() ? EMPTY_RECORDS : c.poll(unit.toMillis(timeout)))
				.sync()
				.whenCompleteSuccessfully(rs -> rs.forEach(r -> channelListener.onNext(ResourceDescriptor.of(r.topic()), convert(r))))
				.whenCompleteExceptionally(channelListener::onError)
				.thenApply(rs -> rs.isEmpty() ? Trigger.NOTHING : Trigger.RESCHEDULE);
	}

	@Override
	public void seek(ResourceDescriptor topic, Revision revision) {
		assignments.computeIfAbsent(topic.value(), t -> client.execute(c -> doFetchPartitionSize(c, t)).sync())
				.whenCompleteSuccessfully(size -> client.perform(c -> doAssignAndSeek(c, topic.value(), revision)))
				.whenCompleteExceptionally(channelListener::onError);
	}

	@Override
	public Promise<Integer> subscribe(ResourceDescriptor topic) {
		return assignments.computeIfAbsent(topic.value(), t -> client.execute(c -> doSubscribe(c, t)));
	}

	@Override
	public void unseek(ResourceDescriptor topic, int partition) {
		client.perform(c -> {
			List<TopicPartition> partitions = c.assignment()
					.stream()
					.filter(tp -> !tp.topic().equals(topic.value()) || tp.partition() != partition)
					.collect(toList());
			c.assign(partitions);
			Set<String> retained = partitions.stream().map(TopicPartition::topic).collect(toSet());
			assignments.keySet().retainAll(retained);
		});
	}

	@Override
	public void unsubscribe(ResourceDescriptor topic) {
		if (assignments.remove(topic.value()) != null) {
			client.perform(c -> c.subscribe(assignments.keySet(), kafkaRebalanceListener));
		}
	}
}
