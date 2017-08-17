package org.ddd4j.infrastructure.channel.kafka;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.ddd4j.Require;
import org.ddd4j.collection.Props;
import org.ddd4j.infrastructure.ChannelName;
import org.ddd4j.infrastructure.ChannelPartition;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.DataAccessFactory;
import org.ddd4j.infrastructure.channel.HotSource;
import org.ddd4j.infrastructure.channel.util.SourceListener;
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
		public HotSource createHotSource(Callback callback, SourceListener<ReadBuffer, ReadBuffer> listener) {
			Scheduler scheduler = context.get(Scheduler.KEY);
			Consumer<byte[], byte[]> consumer = new KafkaConsumer<>(propsFor(null, 200));
			return new KafkaHotSource(scheduler, consumer, callback, listener);
		}
	}

	private static class KafkaRebalanceCallback implements ConsumerRebalanceListener {

		private static Seq<ChannelPartition> toChannelPartitions(Collection<TopicPartition> partitions) {
			return Seq.ofCopied(partitions).map().to(tp -> new ChannelPartition(tp.topic(), tp.partition()));
		}

		private final Consumer<byte[], byte[]> consumer;
		private final Callback callback;

		KafkaRebalanceCallback(Consumer<byte[], byte[]> consumer, Callback callback) {
			this.consumer = Require.nonNull(consumer);
			this.callback = Require.nonNull(callback);
		}

		void onError(Throwable throwable) {
			callback.onError(throwable);
		}

		@Override
		public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
			consumer.seekToEnd(partitions);
			callback.onPartitionsAssigned(toChannelPartitions(partitions));
		}

		@Override
		public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
			callback.onPartitionsRevoked(toChannelPartitions(partitions));
		}

		void onSubscribed(int partitionCount) {
			callback.onSubscribed(partitionCount);
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

	private final SourceListener<ReadBuffer, ReadBuffer> listener;
	private final KafkaRebalanceCallback callback;
	private final Agent<Consumer<byte[], byte[]>> client;
	private final Rescheduler rescheduler;
	private final Map<String, Promise<Integer>> subscriptions;

	KafkaHotSource(Scheduler scheduler, Consumer<byte[], byte[]> consumer, Callback callback,
			SourceListener<ReadBuffer, ReadBuffer> listener) {
		this.listener = Require.nonNull(listener);
		this.callback = new KafkaRebalanceCallback(consumer, callback);
		this.client = scheduler.createAgent(consumer);
		this.rescheduler = scheduler.reschedulerFor(this);
		this.subscriptions = new ConcurrentHashMap<>();
	}

	@Override
	public void closeChecked() throws Exception {
		client.executeBlocked((t, u) -> c -> c.close(t, u)).join();
	}

	@Override
	public Promise<Trigger> onScheduled(Scheduler scheduler) {
		return client.performBlocked((t, u) -> c -> c.subscription().isEmpty() ? EMPTY_RECORDS : c.poll(u.toMillis(t)))
				.whenComplete(rs -> rs.forEach(this::onNext), callback::onError)
				.thenReturn(this::triggering);
	}

	private void onNext(ConsumerRecord<byte[], byte[]> record) {
		listener.onNext(ChannelName.of(record.topic()), convert(record));
	}

	@Override
	public Promise<Integer> subscribe(ChannelName name) {
		return subscriptions.computeIfAbsent(name.value(), this::subscribe);
	}

	private Promise<Integer> subscribe(String topic) {
		Promise<Integer> partitionSize = client.perform(c -> c.partitionsFor(topic).size());
		partitionSize.whenComplete(callback::onSubscribed, callback::onError);
		updateSubscription();
		rescheduler.doIfNecessary();
		return partitionSize;
	}

	private Trigger triggering() {
		return !subscriptions.isEmpty() ? Trigger.NOTHING : Trigger.RESCHEDULE;
	}

	@Override
	public void unsubscribe(ChannelName name) {
		if (subscriptions.remove(name.value()) != null) {
			updateSubscription();
		}
	}

	private void updateSubscription() {
		client.execute(c -> c.subscribe(subscriptions.keySet(), callback));
	}
}
