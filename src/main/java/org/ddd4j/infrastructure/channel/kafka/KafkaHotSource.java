package org.ddd4j.infrastructure.channel.kafka;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;
import static org.ddd4j.infrastructure.channel.DataAccessFactory.committed;
import static org.ddd4j.infrastructure.channel.DataAccessFactory.resetBuffers;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.ddd4j.Require;
import org.ddd4j.collection.Props;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.HotSource;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.BlockingTask;
import org.ddd4j.io.Bytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public class KafkaHotSource implements HotSource, BlockingTask {

	public static class Factory implements HotSource.Factory {

		public Factory(Context context) {
			// TODO Auto-generated constructor stub
		}

		@Override
		public HotSource createHotSource(Callback callback) {
			Consumer<byte[], byte[]> consumer = null;
			new KafkaRebalanceListener(consumer, callback);
			return new KafkaHotSource();
		}
	}

	private static class KafkaRebalanceListener implements ConsumerRebalanceListener {

		private final Consumer<byte[], byte[]> consumer;
		private final Callback callback;

		KafkaRebalanceListener(Consumer<byte[], byte[]> consumer, Callback callback) {
			this.consumer = Require.nonNull(consumer);
			this.callback = Require.nonNull(callback);
		}

		void onError(Throwable throwable) {
			callback.onError(throwable);
		}

		void onSubscribed(int partitionCount) {
			callback.onSubscribed(partitionCount);
		}

		@Override
		public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
			consumer.seekToEnd(partitions);
			partitionsByTopic(partitions).forEach(null);
		}

		@Override
		public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
			partitionsByTopic(partitions).forEach(callback::onPartitionsRevoked);
		}

		private Map<ResourceDescriptor, int[]> partitionsByTopic(Collection<TopicPartition> partitions) {
			return partitions.stream().collect(groupingBy(tp -> new ResourceDescriptor(tp.topic()),
					mapping(TopicPartition::partition, collectingAndThen(toSet(), p -> p.stream().mapToInt(Integer::intValue).toArray()))));
		}
	}

	private static class Subscriptions {

		private final ResourceDescriptor resource;
		private final Promise<Integer> partitionSize;
		private Set<Listener<ReadBuffer, ReadBuffer>> listeners;

		Subscriptions(String topic, Promise<Integer> partitionSize) {
			this.resource = ResourceDescriptor.of(topic);
			this.partitionSize = Require.nonNull(partitionSize);
		}

		Subscriptions add(Listener<ReadBuffer, ReadBuffer> listener) {
			listeners.add(Require.nonNull(listener));
			return this;
		}

		void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
			listeners.forEach(l -> l.onNext(resource, resetBuffers(committed)));
		}

		Promise<Integer> partitionSize() {
			return partitionSize;
		}

		Subscriptions remove(Listener<ReadBuffer, ReadBuffer> listener) {
			listeners.remove(listener);
			return listeners.isEmpty() ? null : this;
		}
	}

	private static final ConsumerRecords<byte[], byte[]> EMPTY_RECORDS = ConsumerRecords.empty();
	private static final Subscriptions NONE = new Subscriptions("<NONE>", Promise.failed(new AssertionError()));

	static Committed<ReadBuffer, ReadBuffer> convert(ConsumerRecord<byte[], byte[]> record) {
		ReadBuffer key = Bytes.wrap(record.key()).buffered();
		ReadBuffer value = Bytes.wrap(record.value()).buffered();
		Revision actual = new Revision(record.partition(), record.offset());
		Revision next = actual.increment(1);
		ZonedDateTime timestamp = Instant.ofEpochMilli(record.timestamp()).atZone(ZoneOffset.UTC);
		// TODO deserialize header?
		Props header = new Props(value);
		return committed(key, value, actual, next, timestamp, header);
	}

	private Agent<Consumer<byte[], byte[]>> client;
	private KafkaRebalanceListener rebalanceListener;
	private Map<String, Subscriptions> subscriptions;
	private Rescheduler rescheduler;

	@Override
	public void closeChecked() throws Exception {
		client.perform(Consumer::close).join();
	}

	@Override
	public Promise<Trigger> executeWith(Executor executor, long timeout, TimeUnit unit) {
		return client.execute(c -> c.subscription().isEmpty() ? EMPTY_RECORDS : c.poll(unit.toMillis(timeout)))
				.whenCompleteSuccessfully(rs -> rs.forEach(r -> subscriptions.getOrDefault(r.topic(), NONE).onNext(convert(r))))
				.whenCompleteExceptionally(rebalanceListener::onError)
				.thenReturn(this::triggering);
	}

	private Trigger triggering() {
		return subscriptions.isEmpty() ? Trigger.NOTHING : Trigger.RESCHEDULE;
	}

	@Override
	public Promise<Integer> subscribe(Listener<ReadBuffer, ReadBuffer> listener, ResourceDescriptor resource) {
		return subscriptions.computeIfAbsent(resource.value(), this::subscribe).add(listener).partitionSize();
	}

	private Subscriptions subscribe(String topic) {
		Promise<Integer> partitionSize = client.execute(c -> c.partitionsFor(topic).size());
		partitionSize.whenCompleteSuccessfully(rebalanceListener::onSubscribed);
		client.perform(c -> c.subscribe(subscriptions.keySet(), rebalanceListener));
		rescheduler.doIfNecessary();
		return new Subscriptions(topic, partitionSize);
	}

	@Override
	public void unsubscribe(Listener<ReadBuffer, ReadBuffer> listener, ResourceDescriptor resource) {
		if (subscriptions.computeIfPresent(resource.value(), (t, s) -> s.remove(listener)) == null) {
			client.perform(c -> c.subscribe(subscriptions.keySet(), rebalanceListener));
		}
	}
}
