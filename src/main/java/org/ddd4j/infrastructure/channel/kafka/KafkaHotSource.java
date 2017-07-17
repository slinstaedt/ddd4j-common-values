package org.ddd4j.infrastructure.channel.kafka;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.ddd4j.Require;
import org.ddd4j.collection.Props;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.ResourcePartition;
import org.ddd4j.infrastructure.channel.DataAccessFactory;
import org.ddd4j.infrastructure.channel.HotSource;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.ScheduledTask;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.Bytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public class KafkaHotSource implements HotSource, ScheduledTask {

	public static class Factory implements HotSource.Factory {

		private final Context context;

		public Factory(Context context) {
			this.context = Require.nonNull(context);
		}

		@Override
		public HotSource createHotSource(Listener<ReadBuffer, ReadBuffer> listener) {
			Scheduler scheduler = context.get(Scheduler.KEY);
			Consumer<byte[], byte[]> consumer = new KafkaConsumer<>(propsFor(null, 200));
			return new KafkaHotSource(scheduler, consumer, listener);
		}
	}

	private static class KafkaRebalanceListener implements ConsumerRebalanceListener {

		private static Seq<ResourcePartition> toResourcePartitions(Collection<TopicPartition> partitions) {
			return Seq.ofCopied(partitions).map().to(tp -> new ResourcePartition(tp.topic(), tp.partition()));
		}

		private final Consumer<byte[], byte[]> consumer;
		private final Listener<ReadBuffer, ReadBuffer> listener;

		KafkaRebalanceListener(Consumer<byte[], byte[]> consumer, Listener<ReadBuffer, ReadBuffer> listener) {
			this.consumer = Require.nonNull(consumer);
			this.listener = Require.nonNull(listener);
		}

		void onError(Throwable throwable) {
			listener.onError(throwable);
		}

		void onNext(ConsumerRecord<byte[], byte[]> record) {
			listener.onNext(ResourceDescriptor.of(record.topic()), convert(record));
		}

		@Override
		public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
			consumer.seekToEnd(partitions);
			listener.onPartitionsAssigned(toResourcePartitions(partitions));
		}

		@Override
		public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
			listener.onPartitionsRevoked(toResourcePartitions(partitions));
		}

		void onSubscribed(int partitionCount) {
			listener.onSubscribed(partitionCount);
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

	static Properties propsFor(Seq<String> servers, int timeout) {
		Properties props = new Properties();
		props.setProperty("bootstrap.servers", String.join(",", servers));
		props.setProperty("group.id", null);
		props.setProperty("enable.auto.commit", "false");
		props.setProperty("heartbeat.interval.ms", String.valueOf(timeout / 4));
		props.setProperty("session.timeout.ms", String.valueOf(timeout));
		return props;
	}

	private final Agent<Consumer<byte[], byte[]>> client;
	private final Rescheduler rescheduler;
	private final KafkaRebalanceListener listener;
	private final Map<String, Promise<Integer>> subscriptions;

	KafkaHotSource(Scheduler scheduler, Consumer<byte[], byte[]> consumer, Listener<ReadBuffer, ReadBuffer> listener) {
		this.client = scheduler.createAgent(consumer);
		this.rescheduler = scheduler.reschedulerFor(this);
		this.listener = new KafkaRebalanceListener(consumer, listener);
		this.subscriptions = new ConcurrentHashMap<>();
	}

	@Override
	public void closeChecked() throws Exception {
		client.perform(Consumer::close).join();
	}

	@Override
	public Promise<Trigger> doScheduled(long timeout, TimeUnit unit) {
		return client.block(c -> c.subscription().isEmpty() ? EMPTY_RECORDS : c.poll(unit.toMillis(timeout)))
				.whenComplete(rs -> rs.forEach(listener::onNext), listener::onError)
				.thenReturn(this::triggering);
	}

	@Override
	public Promise<Integer> subscribe(ResourceDescriptor resource) {
		return subscriptions.computeIfAbsent(resource.value(), this::subscribe);
	}

	private Promise<Integer> subscribe(String topic) {
		Promise<Integer> partitionSize = client.execute(c -> c.partitionsFor(topic).size());
		partitionSize.whenComplete(listener::onSubscribed, listener::onError);
		updateSubscription();
		rescheduler.doIfNecessary();
		return partitionSize;
	}

	private Trigger triggering() {
		return !subscriptions.isEmpty() ? Trigger.NOTHING : Trigger.RESCHEDULE;
	}

	@Override
	public void unsubscribe(ResourceDescriptor resource) {
		if (subscriptions.remove(resource.value()) != null) {
			updateSubscription();
		}
	}

	private void updateSubscription() {
		client.perform(c -> c.subscribe(subscriptions.keySet(), listener));
	}
}
