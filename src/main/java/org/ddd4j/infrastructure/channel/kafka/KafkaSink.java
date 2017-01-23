package org.ddd4j.infrastructure.channel.kafka;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ColdSink;
import org.ddd4j.infrastructure.channel.HotSink;
import org.ddd4j.infrastructure.scheduler.Deferred;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Uncommitted;

public class KafkaSink implements ColdSink, HotSink {

	public static ProducerRecord<byte[], byte[]> convert(ResourceDescriptor topic, Recorded<ReadBuffer, ReadBuffer> recorded) {
		int partition = recorded.getExpected().getPartition();
		long timestamp = Clock.systemUTC().millis();
		byte[] key = recorded.getKey().toByteArray();
		byte[] value = recorded.getValue().toByteArray();
		// TODO serialize header?
		return new ProducerRecord<>(topic.value(), partition, timestamp, key, value);
	}

	private final Scheduler scheduler;
	private final KafkaProducer<byte[], byte[]> client;

	public KafkaSink(Scheduler scheduler, KafkaProducer<byte[], byte[]> client) {
		this.scheduler = Require.nonNull(scheduler);
		this.client = Require.nonNull(client);
	}

	@Override
	public void publish(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed) {
		client.send(convert(topic, committed));
	}

	@Override
	public Promise<CommitResult<ReadBuffer, ReadBuffer>> tryCommit(ResourceDescriptor topic, Uncommitted<ReadBuffer, ReadBuffer> attempt) {
		Deferred<CommitResult<ReadBuffer, ReadBuffer>> deferred = scheduler.createDeferredPromise();
		client.send(convert(topic, attempt), new Callback() {

			@Override
			public void onCompletion(RecordMetadata metadata, Exception exception) {
				if (metadata != null) {
					long nextExpected = metadata.offset() + 1;
					ZonedDateTime timestamp = Instant.ofEpochMilli(metadata.timestamp()).atZone(ZoneOffset.UTC);
					deferred.completeSuccessfully(attempt.committed(nextExpected, timestamp));
				} else {
					deferred.completeExceptionally(exception);
				}
			}
		});
		return deferred;
	}
}
