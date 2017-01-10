package org.ddd4j.infrastructure.source.kafka;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.ddd4j.collection.Cache;
import org.ddd4j.collection.Cache.Decorating.Evicting.EvictStrategy;
import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.scheduler.LoopedTask;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.infrastructure.source.ColdSource;
import org.ddd4j.infrastructure.source.Subscriber;
import org.ddd4j.io.buffer.Bytes;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;

public class KafkaColdSource implements ColdSource {

	public static Committed<Bytes, Bytes> convert(ConsumerRecord<byte[], byte[]> r) {
		Bytes key = Bytes.wrap(r.key());
		Bytes value = Bytes.wrap(r.value());
		Revision actual = new Revision(r.partition(), r.offset());
		Revision expected = actual.increment(1);
		ZonedDateTime timestamp = Instant.ofEpochMilli(r.timestamp()).atZone(ZoneOffset.UTC);
		return new Committed<>(key, value, actual, expected, timestamp);
	}

	private class Reader implements LoopedTask, ConsumerRebalanceListener, AutoCloseable {

		private final Consumer<byte[], byte[]> client;
		private final Subscriber subscriber;
		private final Revisions revisions;

		Reader(Consumer<byte[], byte[]> client, Subscriber subscriber) {
			this.client = Require.nonNull(client);
			this.subscriber = Require.nonNull(subscriber);
			this.revisions = new Revisions(-1);
		}

		@Override
		public void loop(long duration, TimeUnit unit) throws Exception {
			for (ConsumerRecord<byte[], byte[]> record : client.poll(unit.toMillis(duration))) {
				Committed<Bytes, Bytes> committed = convert(record);
				subscriber.onCommitted(committed);
				revisions.update(record.partition(), record.offset());
			}
		}

		@Override
		public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
			Revisions revisions = subscriber.loadRevisions();
			partitions.forEach(p -> client.seek(p, revisions.offset(p.partition())));
		}

		@Override
		public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
			// TODO to much saved?
			subscriber.saveRevisions(revisions);
		}

		@Override
		public void close() {
			clientPool.release(client);
		}
	}

	private Scheduler scheduler;
	private Map<String, List<PartitionInfo>> partitionInfos;
	private final Cache.Pool<Consumer<byte[], byte[]>> clientPool;

	public KafkaColdSource() {
		clientPool = Cache.<Consumer<byte[], byte[]>>exclusiveOnIdentity()
				.on()
				.evicted(Consumer::close)
				.released(Consumer::unsubscribe)
				.evict(EvictStrategy.LAST_RELEASED)
				.whenThresholdReached(60, TimeUnit.SECONDS)
				.withMinimumCapacity(1) // permanent client for HotSource
				.lookupRandomValues(() -> new KafkaConsumer<>(new Properties()));
	}

	@Override
	public void load(ResourceDescriptor topic, Subscriber subscriber) {
		Consumer<byte[], byte[]> client = clientPool.acquire();
		Reader reader = new Reader(client, subscriber);
		client.subscribe(Collections.singleton(topic.value()), reader);
		reader.scheduleWith(scheduler, 1, TimeUnit.SECONDS);
	}
}
