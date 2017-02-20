package org.ddd4j.infrastructure.channel.kafka;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.Channel.ColdCallback;
import org.ddd4j.infrastructure.channel.Channel.HotCallback;
import org.ddd4j.infrastructure.channel.ChannelListener;
import org.ddd4j.infrastructure.channel.PartitionRebalanceListener;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.BlockingTask;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.Bytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.collection.Props;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public class KafkaCallback2 implements ColdCallback, HotCallback, BlockingTask {

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

	private static final ConsumerRecords<byte[], byte[]> EMPTY_RECORDS = new ConsumerRecords<>(Collections.emptyMap());

	static Committed<ReadBuffer, ReadBuffer> convert(ConsumerRecord<byte[], byte[]> record) {
		ReadBuffer key = Bytes.wrap(record.key()).buffered();
		ReadBuffer value = Bytes.wrap(record.value()).buffered();
		Revision actual = new Revision(record.partition(), record.offset());
		Revision next = actual.increment(1);
		ZonedDateTime timestamp = Instant.ofEpochMilli(record.timestamp()).atZone(ZoneOffset.UTC);
		// TODO deserialize header?
		Props header = new Props(value);
		return new Committed<>(key, value, actual, next, timestamp, header);
	}

	private final Agent<Consumer<byte[], byte[]>> client;
	private final ChannelListener channelListener;
	private final KafkaRebalanceListener kafkaRebalanceListener;

	public KafkaCallback2(Scheduler scheduler, Consumer<byte[], byte[]> consumer, ChannelListener channelListener,
			PartitionRebalanceListener rebalanceListener) {
		this.client = scheduler.createAgent(consumer);
		this.channelListener = Require.nonNull(channelListener);
		this.kafkaRebalanceListener = new KafkaRebalanceListener(consumer, rebalanceListener);
	}

	@Override
	public void closeChecked() {
		client.perform(Consumer::close);
	}

	@Override
	public Promise<Integer> partitionSize(ResourceDescriptor topic) {
		return client.execute(c -> c.partitionsFor(topic.value()).size());
	}

	@Override
	public Promise<Trigger> scheduleWith(Executor executor, long timeout, TimeUnit unit) {
		return client.execute(c -> c.assignment().isEmpty() ? EMPTY_RECORDS : c.poll(unit.toMillis(timeout)))
				.sync()
				.whenCompleteSuccessfully(rs -> rs.forEach(r -> channelListener.onNext(ResourceDescriptor.of(r.topic()), convert(r))))
				.whenCompleteExceptionally(channelListener::onError)
				.handleSuccess(rs -> rs == EMPTY_RECORDS ? Trigger.NOTHING : Trigger.RESCHEDULE);
	}

	@Override
	public void seek(ResourceDescriptor topic, Revision revision) {
		client.perform(c -> c.seek(new TopicPartition(topic.value(), revision.getPartition()), revision.getOffset()));
	}

	@Override
	public void updateAssignment(Map<ResourceDescriptor, IntStream> assignments) {
		List<TopicPartition> partitions = assignments.entrySet()
				.stream()
				.flatMap(e -> e.getValue().mapToObj(p -> new TopicPartition(e.getKey().value(), p)))
				.collect(toList());
		client.perform(c -> c.assign(partitions));
	}

	@Override
	public void updateSubscription(Set<ResourceDescriptor> topics) {
		Set<String> subscriptions = topics.stream().map(ResourceDescriptor::value).collect(toSet());
		client.perform(c -> c.subscribe(subscriptions, kafkaRebalanceListener));
	}
}
