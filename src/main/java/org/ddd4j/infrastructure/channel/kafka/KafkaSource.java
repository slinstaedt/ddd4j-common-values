package org.ddd4j.infrastructure.channel.kafka;

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
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ColdSource;
import org.ddd4j.infrastructure.channel.HotSource;
import org.ddd4j.infrastructure.channel.Subscriber;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.BlockingTask;
import org.ddd4j.infrastructure.scheduler.Requesting;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.buffer.Bytes;
import org.ddd4j.io.buffer.ReadBuffer;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;
import org.reactivestreams.Subscription;

public class KafkaSource implements ColdSource, HotSource, BlockingTask, ConsumerRebalanceListener {

	private class TopicSubscribers {

		private class TopicSubscription implements Subscription {

			private final Subscriber subscriber;
			private final Revisions expected;
			private final boolean checkEndOfStream;
			private final Requesting requesting;

			TopicSubscription(Subscriber subscriber, int partitionSize, boolean checkEndOfStream) {
				this.subscriber = Require.nonNull(subscriber);
				this.expected = new Revisions(partitionSize);
				this.checkEndOfStream = checkEndOfStream;
				this.requesting = new Requesting();
				subscriber.onSubscribe(this);
			}

			@Override
			public void cancel() {
				KafkaSource.this.unsubscribe(topic, subscriber);
			}

			void loadRevisions(int[] partitions) {
				subscriber.loadRevisions(partitions).forEach(expected::update);
			}

			void onError(Throwable throwable) {
				subscriber.onError(throwable);
				KafkaSource.this.unsubscribe(topic, subscriber);
			}

			void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
				if (!checkEndOfStream && !requesting.hasRemaining()) {
					cancel();
					subscriber.onComplete();
				} else if (!expected.reachedBy(committed.getActual())) {
					subscriber.onNext(committed);
					expected.update(committed.getExpected());
					endOffsets(topic).handleSuccess(r -> r.reachedBy(expected));
					if (checkEndOfStream && endOffsets(topic).reachedBy(expected)) {
						cancel();
						subscriber.onComplete();
					}
				}
			}

			@Override
			public void request(long n) {
				requesting.more(n);
			}

			void saveRevisions() {
				subscriber.saveRevisions(expected);
			}
		}

		private final String topic;
		private final Map<Subscriber, TopicSubscription> subscribers;
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

		void read(Subscriber subscriber) {
			subscribers.computeIfAbsent(subscriber, s -> new TopicSubscription(s, partitionSize, true));
		}

		void saveRevisions() {
			subscribers.values().forEach(s -> s.saveRevisions());
		}

		void subscribe(Subscriber subscriber) {
			subscribers.computeIfAbsent(subscriber, s -> new TopicSubscription(s, partitionSize, false));
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

	private final Agent<Consumer<byte[], byte[]>> client;
	private final Map<String, TopicSubscribers> subscriptions;

	public KafkaSource(Scheduler scheduler, Properties properties) {
		this.client = scheduler.createAgent(new KafkaConsumer<>(properties, DESERIALIZER, DESERIALIZER));
		this.subscriptions = new ConcurrentHashMap<>();
	}

	@Override
	public void close() {
		client.perform(Consumer::close).sync().whenComplete((c, e) -> subscriptions.clear());
	}

	private Promise<Revisions> endOffsets(String topic) {
		return partitionSize(topic).handleSuccess(partitionSize -> {
			Revisions revisions = new Revisions(partitionSize);
			List<TopicPartition> partitions = IntStream.range(0, partitionSize).mapToObj(p -> new TopicPartition(topic, p)).collect(Collectors.toList());
			client.perform(c -> c.endOffsets(partitions).entrySet().forEach(e -> revisions.updateWithPartition(e.getKey().partition(), e.getValue())));
			return revisions;
		});
	}

	@Override
	public Trigger perform(long timeout, TimeUnit unit) throws Exception {
		client.execute(c -> subscriptions.isEmpty() ? ConsumerRecords.<byte[], byte[]>empty() : c.poll(unit.toMillis(timeout)))
				.sync()
				.whenCompleteSuccessfully(records -> records.forEach(r -> subscriptions.get(r.topic()).onNext(convert(r))))
				.whenCompleteExceptionally(ex -> subscriptions.values().forEach(s -> s.onError(ex)));
		return subscriptions.isEmpty() ? Trigger.NOTHING : Trigger.RESCHEDULE;
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

	private Promise<Integer> partitionSize(String topic) {
		return client.execute(c -> c.partitionsFor(topic).size());
	}

	@Override
	public void read(ResourceDescriptor topic, Subscriber subscriber) {
		subscriptions.computeIfAbsent(topic.value(), this::subscribeTo).read(subscriber);
	}

	@Override
	public void subscribe(ResourceDescriptor topic, Subscriber subscriber) {
		subscriptions.computeIfAbsent(topic.value(), this::subscribeTo).subscribe(subscriber);
	}

	private TopicSubscribers subscribeTo(String topic) {
		Set<String> topics = new HashSet<>(subscriptions.keySet());
		topics.add(Require.nonEmpty(topic));
		client.perform(c -> c.subscribe(topics, this));
		return new TopicSubscribers(topic);
	}

	private void unsubscribe(String topic, Subscriber subscriber) {
		subscriptions.computeIfPresent(topic, (t, s) -> {
			if (s.unsubscribe(subscriber)) {
				client.perform(c -> c.subscribe(subscriptions.keySet(), this));
				return null;
			} else {
				return s;
			}
		});
	}
}
