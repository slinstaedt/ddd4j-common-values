package org.ddd4j.infrastructure.channel.kafka;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
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
import org.ddd4j.infrastructure.channel.DataAccessFactory;
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

		private final Context context;

		public Factory(Context context) {
			this.context = Require.nonNull(context);
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

		@Override
		public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
			consumer.seekToEnd(partitions);
			partitionsByTopic(partitions).forEach(null);
		}

		@Override
		public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
			partitionsByTopic(partitions).forEach(callback::onPartitionsRevoked);
		}

		void onSubscribed(int partitionCount) {
			callback.onSubscribed(partitionCount);
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
		Props header = new Props(value);
		return DataAccessFactory.committed(key, value, actual, next, timestamp, header);
	}

	private Agent<Consumer<byte[], byte[]>> client;
	private KafkaRebalanceListener rebalanceListener;
	private Rescheduler rescheduler;
	private final Subscriptions subscriptions;

	public KafkaHotSource() {
		// TODO Auto-generated constructor stub
		this.subscriptions = new Subscriptions(this::onSubscribe);
	}

	@Override
	public void closeChecked() throws Exception {
		client.perform(Consumer::close).join();
	}

	@Override
	public Promise<Trigger> executeWith(Executor executor, long timeout, TimeUnit unit) {
		return client.execute(c -> c.subscription().isEmpty() ? EMPTY_RECORDS : c.poll(unit.toMillis(timeout)))
				.whenCompleteSuccessfully(rs -> rs.forEach(r -> subscriptions.onNext(r.topic(), convert(r))))
				.whenCompleteExceptionally(rebalanceListener::onError)
				.thenReturn(this::triggering);
	}

	private Listeners onSubscribe(String topic) {
		Promise<Integer> partitionSize = client.execute(c -> c.partitionsFor(topic).size());
		partitionSize.whenCompleteSuccessfully(rebalanceListener::onSubscribed);
		updateSubscription();
		rescheduler.doIfNecessary();
		return new Listeners(topic, partitionSize, this::updateSubscription);
	}

	@Override
	public Promise<Integer> subscribe(Listener<ReadBuffer, ReadBuffer> listener, ResourceDescriptor resource) {
		return subscriptions.subscribe(listener, resource);
	}

	private Trigger triggering() {
		return !subscriptions.isEmpty() ? Trigger.NOTHING : Trigger.RESCHEDULE;
	}

	@Override
	public void unsubscribe(Listener<ReadBuffer, ReadBuffer> listener, ResourceDescriptor resource) {
		subscriptions.unsubscribe(listener, resource);
	}

	private void updateSubscription() {
		client.perform(c -> c.subscribe(subscriptions.resources(), rebalanceListener));
	}
}
