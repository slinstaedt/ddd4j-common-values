package org.ddd4j.infrastructure.pipe.kafka;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.pipe.ColdSource;
import org.ddd4j.infrastructure.pipe.HotSource;
import org.ddd4j.infrastructure.pipe.Subscriber;
import org.ddd4j.infrastructure.scheduler.Actor;
import org.ddd4j.infrastructure.scheduler.LoopedTask;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.buffer.Bytes;
import org.ddd4j.io.buffer.ReadBuffer;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;
import org.reactivestreams.Subscription;

public class KafkaSource implements ColdSource, HotSource, LoopedTask, ConsumerRebalanceListener {

	private static class KafkaSubscription implements Subscription {

		private final String topic;
		private final Subscriber subscriber;
		private final boolean checkEndOfStream;
		private final Revisions expected;

		KafkaSubscription(String topic, Subscriber subscriber, boolean checkEndOfStream) {
			this.topic = Require.nonEmpty(topic);
			this.subscriber = Require.nonNull(subscriber);
			this.checkEndOfStream = checkEndOfStream;
			this.expected = new Revisions(partitionSize(topic));
			subscriber.onSubscribe(this);
		}

		@Override
		public void cancel() {
			unsubscribe(topic, subscriber);
		}

		void loadRevisions(int[] partitions) {
			subscriber.loadRevisions(partitions).forEach(expected::update);
		}

		void onError(Throwable throwable) {
			subscriber.onError(throwable);
			unsubscribe(topic, subscriber);
		}

		void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
			if (expected.reachedBy(committed.getActual())) {
				return;
			}
			subscriber.onNext(committed);
			expected.update(committed.getExpected());
			if (checkEndOfStream && endOffsets(topic).reachedBy(expected)) {
				cancel();
				subscriber.onComplete();
			}
		}

		@Override
		public void request(long n) {
			// TODO ignore?
			throw new UnsupportedOperationException();
		}

		void saveRevisions() {
			subscriber.saveRevisions(expected);
		}
	}

	private static class TopicSubscribers {

		private final String topic;
		private final Map<Subscriber, KafkaSubscription> subscribers;
		private int partitionSize;

		TopicSubscribers(String topic) {
			this.topic = Require.nonEmpty(topic);
			this.subscribers = new ConcurrentHashMap<>();
		}

		void loadRevisions(int[] partitions) {
			subscribers.values().forEach(s -> s.loadRevisions(partitions));
		}

		void onError(Throwable throwable) {
			subscribers.values().forEach(c -> c.onError(throwable));
		}

		void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
			subscribers.values().forEach(c -> c.onNext(committed));
		}

		void read(String topic, Subscriber subscriber) {
			subscribers.computeIfAbsent(subscriber, s -> new KafkaSubscription(topic, s, true));
		}

		void saveRevisions() {
			subscribers.values().forEach(s -> s.saveRevisions());
		}

		void subscribe(String topic, Subscriber subscriber) {
			subscribers.computeIfAbsent(subscriber, s -> new KafkaSubscription(topic, s, false));
		}

		boolean unsubscribe(Subscriber subscriber) {
			subscribers.remove(subscriber);
			return subscribers.isEmpty();
		}
	}

	private static final ByteArrayDeserializer DESERIALIZER = new ByteArrayDeserializer();

	static Committed<ReadBuffer, ReadBuffer> convert(ConsumerRecord<byte[], byte[]> record) {
		ReadBuffer key = Bytes.wrap(record.key()).buffered();
		ReadBuffer value = Bytes.wrap(record.value()).buffered();
		Revision actual = new Revision(record.partition(), record.offset());
		Revision expected = actual.increment(1);
		ZonedDateTime timestamp = Instant.ofEpochMilli(record.timestamp()).atZone(ZoneOffset.UTC);
		return new Committed<>(key, value, actual, expected, timestamp);
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

	private final Scheduler scheduler;
	private final Actor<Consumer<byte[], byte[]>> actor;
	private final KafkaConsumer<byte[], byte[]> client;
	private final Map<String, TopicSubscribers> subscriptions;

	public KafkaSource(Scheduler scheduler, Properties properties) {
		this.scheduler = Require.nonNull(scheduler);
		actor = scheduler.createActor(new KafkaConsumer<>(properties, DESERIALIZER, DESERIALIZER));
		this.client = new KafkaConsumer<>(properties, DESERIALIZER, DESERIALIZER);
		this.subscriptions = new ConcurrentHashMap<>();
	}

	private Revisions endOffsets(String topic) {
		int partitionSize = partitionSize(topic);
		Revisions revisions = new Revisions(partitionSize);
		List<TopicPartition> partitions = IntStream.range(0, partitionSize).mapToObj(p -> new TopicPartition(topic, p)).collect(Collectors.toList());
		client.endOffsets(partitions).entrySet().forEach(e -> revisions.update(e.getKey().partition(), e.getValue().longValue()));
		return revisions;
	}

	@Override
	public void loop(long duration, TimeUnit unit) throws Exception {
		try {
			if (!client.subscription().isEmpty()) {
				ConsumerRecords<byte[], byte[]> records = client.poll(unit.toMillis(duration));
				for (ConsumerRecord<byte[], byte[]> record : records) {
					Committed<ReadBuffer, ReadBuffer> committed = convert(record);
					subscriptions.get(record.topic()).onNext(committed);
				}
			}
		} catch (Throwable e) {
			subscriptions.values().forEach(s -> s.onError(e));
		}
	}

	@Override
	public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
		partitions.stream().map(TopicPartition::topic).distinct().forEach(topic -> {
			int[] p = partitions.stream().filter(tp -> tp.topic().equals(topic)).mapToInt(TopicPartition::partition).toArray();
			subscriptions.get(topic).loadRevisions(p);
		});
	}

	@Override
	public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
		partitions.stream().map(TopicPartition::topic).distinct().forEach(topic -> {
			subscriptions.get(topic).saveRevisions();
		});
	}

	private int partitionSize(String topic) {
		return client.partitionsFor(topic).size();
	}

	@Override
	public void read(ResourceDescriptor descriptor, Subscriber subscriber) {
		String topic = descriptor.value();
		subscriptions.computeIfAbsent(topic, this::subscribeTo).read(topic, subscriber);
	}

	@Override
	public void subscribe(ResourceDescriptor descriptor, Subscriber subscriber) {
		String topic = descriptor.value();
		subscriptions.computeIfAbsent(topic, this::subscribeTo).subscribe(topic, subscriber);
	}

	private TopicSubscribers subscribeTo(String topic) {
		Set<String> topics = new HashSet<>(subscriptions.keySet());
		topics.add(topic);
		actor.perform(c -> c.subscribe(topics, this));
		return new TopicSubscribers(topic);
	}

	private void unsubscribe(String topic, Subscriber subscriber) {
		subscriptions.computeIfPresent(topic, (t, s) -> {
			if (s.unsubscribe(subscriber)) {
				actor.perform(c -> c.subscribe(subscriptions.keySet(), this));
				return null;
			} else {
				return s;
			}
		});
	}
}
