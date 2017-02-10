package org.ddd4j.infrastructure.channel.kafka;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Properties;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ChannelListener;
import org.ddd4j.infrastructure.channel.ColdChannel;
import org.ddd4j.infrastructure.channel.HotChannel;
import org.ddd4j.infrastructure.scheduler.Deferred;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Uncommitted;

public class KafkaChannel implements ColdChannel, HotChannel {

	private static final ByteArrayDeserializer DESERIALIZER = new ByteArrayDeserializer();

	static ProducerRecord<byte[], byte[]> convert(ResourceDescriptor topic, Recorded<ReadBuffer, ReadBuffer> recorded) {
		int partition = recorded.partition(ReadBuffer::hash);
		long timestamp = Clock.systemUTC().millis();
		byte[] key = recorded.getKey().toByteArray();
		byte[] value = recorded.getValue().toByteArray();
		// TODO serialize header?
		recorded.getHeader();
		return new ProducerRecord<>(topic.value(), partition, timestamp, key, value);
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
	private Producer<byte[], byte[]> client;

	public KafkaChannel(Scheduler scheduler) {
		this.scheduler = Require.nonNull(scheduler);
	}

	@Override
	public void closeChecked() {
		client.close();
	}

	@Override
	public void publish(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed) {
		// TODO message resent on channel reuse as hot and cold?
		client.send(convert(topic, committed));
	}

	@Override
	public void register(ChannelListener listener) {
		// TODO configure client
		KafkaConsumer<byte[], byte[]> client = new KafkaConsumer<>(new Properties(), DESERIALIZER, DESERIALIZER);
		KafkaCallback callback = new KafkaCallback(scheduler, client, listener);
		listener.onRegistration(callback);
	}

	@Override
	public Promise<CommitResult<ReadBuffer, ReadBuffer>> tryCommit(ResourceDescriptor topic, Uncommitted<ReadBuffer, ReadBuffer> attempt) {
		Deferred<CommitResult<ReadBuffer, ReadBuffer>> deferred = scheduler.createDeferredPromise();
		client.send(convert(topic, attempt), (metadata, exception) -> {
			if (metadata != null) {
				Revision nextExpected = new Revision(metadata.partition(), metadata.offset() + 1);
				ZonedDateTime timestamp = Instant.ofEpochMilli(metadata.timestamp()).atZone(ZoneOffset.UTC);
				deferred.completeSuccessfully(attempt.committed(nextExpected, timestamp));
			} else {
				deferred.completeExceptionally(exception);
			}
		});
		return deferred;
	}
}
