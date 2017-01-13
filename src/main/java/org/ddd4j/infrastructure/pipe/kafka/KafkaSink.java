package org.ddd4j.infrastructure.pipe.kafka;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Outcome;
import org.ddd4j.infrastructure.Outcome.CompletableOutcome;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.pipe.ColdSink;
import org.ddd4j.infrastructure.pipe.HotSink;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.buffer.Bytes;
import org.ddd4j.io.buffer.ReadBuffer;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Uncommitted;

public class KafkaSink implements ColdSink, HotSink {

	public static ProducerRecord<byte[], byte[]> convert(String topic, Uncommitted<Bytes, Bytes> record) {
		int partition = record.getExpected().getPartition();
		long timestamp = Clock.systemUTC().millis();
		byte[] key = record.getKey().toByteArray();
		byte[] value = record.getValue().toByteArray();
		return new ProducerRecord<>(topic, partition, timestamp, key, value);
	}

	private final Scheduler scheduler;
	private final KafkaProducer<byte[], byte[]> client;

	public KafkaSink(Scheduler scheduler, KafkaProducer<byte[], byte[]> client) {
		this.scheduler = Require.nonNull(scheduler);
		this.client = Require.nonNull(client);
	}

	@Override
	public Outcome<CommitResult<Bytes, Bytes>> tryCommit(ResourceDescriptor topic, Uncommitted<Bytes, Bytes> attempt) {
		CompletableOutcome<CommitResult<Bytes, Bytes>> outcome = scheduler.createCompletableOutcome();
		ProducerRecord<byte[], byte[]> record = convert(topic.value(), attempt);
		client.send(record, new Callback() {

			@Override
			public void onCompletion(RecordMetadata metadata, Exception exception) {
				if (metadata != null) {
					long nextExpected = metadata.offset() + 1;
					ZonedDateTime timestamp = Instant.ofEpochMilli(metadata.timestamp()).atZone(ZoneOffset.UTC);
					outcome.completeSuccessfully(attempt.committed(nextExpected, timestamp));
				} else {
					outcome.completeExceptionally(exception);
				}
			}
		});
		return outcome;
	}

	@Override
	public void publish(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed) {
		ProducerRecord<byte[], byte[]> record = convert(topic.value(), committed);
		client.send(record);
	}
}
