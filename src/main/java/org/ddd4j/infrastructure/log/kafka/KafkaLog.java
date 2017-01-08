package org.ddd4j.infrastructure.log.kafka;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Outcome;
import org.ddd4j.infrastructure.Outcome.CompletableOutcome;
import org.ddd4j.infrastructure.Result;
import org.ddd4j.infrastructure.log.Log;
import org.ddd4j.infrastructure.scheduler.ColdSource;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.buffer.ReadBuffer;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;
import org.ddd4j.value.versioned.Uncommitted;

public class KafkaLog implements Log<ReadBuffer, ReadBuffer>, ColdSource<Committed<ReadBuffer, Seq<ReadBuffer>>>, ConsumerRebalanceListener {

	private class ConsumerCursor implements Cursor<Committed<ReadBuffer, Seq<ReadBuffer>>> {

		@Override
		public void closeChecked() throws Exception {
		}

		@Override
		public void position(Revision position) throws Exception {
			consumer.position(new TopicPartition(topic, position.getPartition()));
		}

		@Override
		public Seq<? extends Committed<ReadBuffer, Seq<ReadBuffer>>> requestNext(int n) throws Exception {
			// TODO Auto-generated method stub
			return null;
		}
	}

	private static class OutcomeCallback implements Callback {

		private final CompletableOutcome<CommitResult<ReadBuffer, Seq<ReadBuffer>>> outcome;
		private final Uncommitted<ReadBuffer, Seq<ReadBuffer>> attempt;

		OutcomeCallback(CompletableOutcome<CommitResult<ReadBuffer, Seq<ReadBuffer>>> outcome, Uncommitted<ReadBuffer, Seq<ReadBuffer>> attempt) {
			this.outcome = Require.nonNull(outcome);
			this.attempt = Require.nonNull(attempt);
		}

		@Override
		public void onCompletion(RecordMetadata metadata, Exception exception) {
			if (metadata != null) {
				ZonedDateTime timestamp = Instant.ofEpochMilli(metadata.timestamp()).atZone(ZoneId.systemDefault());
				// XXX offset correct?
				Committed<ReadBuffer, Seq<ReadBuffer>> committed = attempt.committed(metadata.offset() + 1, timestamp);
				outcome.completeSuccessfully(committed);
			} else {
				outcome.completeExceptionally(exception);
			}
		}
	}

	private final Scheduler scheduler;
	private final String topic;
	private final Consumer<byte[], byte[]> consumer;
	private final Producer<byte[], byte[]> producer;
	private Revisions revisions;

	public KafkaLog(Scheduler scheduler, Consumer<byte[], byte[]> consumer, Producer<byte[], byte[]> producer, String topic) {
		this.scheduler = Require.nonNull(scheduler);
		this.consumer = Require.nonNull(consumer);
		this.producer = Require.nonNull(producer);
		this.topic = Require.nonEmpty(topic);
	}

	@Override
	public void closeChecked() throws Exception {
		// TODO Auto-generated method stub
	}

	void connect() {
		revisions = Revisions.initial(consumer.partitionsFor(topic).size());
		consumer.subscribe(Collections.singleton(topic), this);
	}

	@Override
	public Revisions currentRevisions() throws Exception {
		return revisions;
	}

	@Override
	public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
		for (TopicPartition partition : partitions) {
			long nextOffset = consumer.position(partition);
			revisions = revisions.next(partition.partition(), nextOffset);
		}
	}

	@Override
	public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
		// TODO Auto-generated method stub
	}

	@Override
	public Cursor<Committed<ReadBuffer, Seq<ReadBuffer>>> open() throws Exception {
		// TODO Auto-generated method stub
		return new ConsumerCursor();
	}

	@Override
	public Result<Committed<ReadBuffer, Seq<ReadBuffer>>> publisher(Revisions startAt, boolean completeOnEnd) {
		return scheduler.createResult(this, startAt, completeOnEnd);
	}

	@Override
	public Outcome<CommitResult<ReadBuffer, Seq<ReadBuffer>>> tryCommit(Uncommitted<ReadBuffer, Seq<ReadBuffer>> attempt) {
		int partition = revisions.partition(attempt.getIdentifier());
		byte[] key = null;
		byte[] value = null;
		ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(topic, partition, System.currentTimeMillis(), key, value);
		CompletableOutcome<CommitResult<ReadBuffer, Seq<ReadBuffer>>> outcome = scheduler.createCompletableOutcome();
		producer.send(record, new OutcomeCallback(outcome, attempt));
		return outcome;
	}
}
