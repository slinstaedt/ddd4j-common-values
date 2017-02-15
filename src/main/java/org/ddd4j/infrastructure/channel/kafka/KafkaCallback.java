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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ChannelListener;
import org.ddd4j.infrastructure.channel.ChannelListener.ColdChannelCallback;
import org.ddd4j.infrastructure.channel.ChannelListener.HotChannelCallback;
import org.ddd4j.infrastructure.channel.PartitionAssignments;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.BlockingTask;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.Bytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.Throwing.TConsumer;
import org.ddd4j.value.collection.Props;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public class KafkaCallback implements ColdChannelCallback, HotChannelCallback, BlockingTask {

	private class KafkaRebalanceListener implements ConsumerRebalanceListener {

		private final Consumer<byte[], byte[]> consumer;

		KafkaRebalanceListener(Consumer<byte[], byte[]> consumer) {
			this.consumer = Require.nonNull(consumer);
		}

		@Override
		public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
			consumer.seekToEnd(partitions);
			partitions.forEach(tp -> assignments(tp.topic(), a -> a.assigned(tp.partition())));
			partitionsByTopic(partitions).forEach(listener::onPartitionsAssigned);
		}

		@Override
		public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
			partitions.forEach(tp -> assignments(tp.topic(), a -> a.unassigned(tp.partition())));
			partitionsByTopic(partitions).forEach(listener::onPartitionsRevoked);
		}

		private Map<ResourceDescriptor, IntStream> partitionsByTopic(Collection<TopicPartition> partitions) {
			return partitions.stream().collect(groupingBy(tp -> new ResourceDescriptor(tp.topic()),
					mapping(TopicPartition::partition, collectingAndThen(toSet(), p -> p.stream().mapToInt(Integer::intValue)))));
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
	private final ChannelListener listener;
	private final KafkaRebalanceListener rebalanceListener;
	private final Map<String, Promise<PartitionAssignments>> assignments;

	public KafkaCallback(Scheduler scheduler, Consumer<byte[], byte[]> consumer, ChannelListener listener) {
		this.client = scheduler.createAgent(consumer);
		this.listener = Require.nonNull(listener);
		this.rebalanceListener = new KafkaRebalanceListener(consumer);
		this.assignments = new ConcurrentHashMap<>();
	}

	@Override
	public void closeChecked() {
		client.perform(Consumer::close);
	}

	private void assignments(String topic, TConsumer<PartitionAssignments> action) {
		assignments.get(topic).sync().whenCompleteSuccessfully(action);
	}

	private void doAssign(Consumer<?, ?> consumer, ResourceDescriptor topic, Revision revision) {
		if (consumer.subscription().isEmpty()) {
			List<TopicPartition> partitions = new ArrayList<>(consumer.assignment());
			partitions.add(new TopicPartition(topic.value(), revision.getPartition()));
			consumer.assign(partitions);
		} else {
			doSubscribe(consumer, topic);
		}
		consumer.seek(new TopicPartition(topic.value(), revision.getPartition()), revision.getOffset());
	}

	private PartitionAssignments doFetchPartitionLayout(Consumer<?, ?> consumer, ResourceDescriptor topic) {
		int partitionSize = consumer.partitionsFor(topic.value()).size();
		return new PartitionAssignments(topic, partitionSize);
	}

	private PartitionAssignments doSubscribe(Consumer<?, ?> consumer, ResourceDescriptor topic) {
		consumer.subscribe(assignments.keySet(), rebalanceListener);
		return doFetchPartitionLayout(consumer, topic);
	}

	@Override
	public void pause(ResourceDescriptor topic) {
		// TODO Auto-generated method stub
	}

	@Override
	public Promise<Trigger> scheduleWith(Executor executor, long timeout, TimeUnit unit) {
		return client.execute(c -> c.assignment().isEmpty() ? EMPTY_RECORDS : c.poll(unit.toMillis(timeout)))
				.sync()
				.whenCompleteSuccessfully(rs -> rs.forEach(r -> listener.onNext(ResourceDescriptor.of(r.topic()), convert(r))))
				.whenCompleteExceptionally(listener::onError)
				.handleSuccess(rs -> rs == EMPTY_RECORDS ? Trigger.NOTHING : Trigger.RESCHEDULE);
	}

	@Override
	public void seek(ResourceDescriptor topic, Revision revision) {
		assignments.computeIfAbsent(topic.value(), t -> client.execute(c -> doFetchPartitionLayout(c, topic)))
				.sync()
				.testAndFail(a -> a.assigned(revision.getPartition()))
				.whenCompleteSuccessfully(a -> client.perform(c -> doAssign(c, topic, revision)));
	}

	@Override
	public Promise<Integer> subscribe(ResourceDescriptor topic) {
		return assignments.computeIfAbsent(topic.value(), t -> client.execute(c -> doSubscribe(c, topic)))
				.handleSuccess(PartitionAssignments::partitionSize);
	}

	@Override
	public void unpause(ResourceDescriptor topic) {
		// TODO Auto-generated method stub
	}

	@Override
	public void unseek(ResourceDescriptor topic) {
		if (assignments.remove(topic.value()) != null) {
			client.perform(c -> {
				Predicate<String> isAssigned = assignments.keySet()::contains;
				List<TopicPartition> partitions = c.assignment().stream().filter(tp -> isAssigned.test(tp.topic())).collect(toList());
				c.assign(partitions);
			});
		}
	}

	@Override
	public void unsubscribe(ResourceDescriptor topic) {
		if (assignments.remove(topic.value()) != null) {
			client.perform(c -> c.subscribe(assignments.keySet(), rebalanceListener));
		}
	}
}
