package org.ddd4j.infrastructure.channel.kafka;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.function.Supplier;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ColdChannel;
import org.ddd4j.infrastructure.channel.HotChannel;
import org.ddd4j.infrastructure.channel.LazyListener;
import org.ddd4j.infrastructure.scheduler.Deferred;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.Lazy;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Uncommitted;

public class KafkaChannel implements ColdChannel, HotChannel {

	static ProducerRecord<byte[], byte[]> convert(ResourceDescriptor topic, Recorded<ReadBuffer, ReadBuffer> recorded) {
		int partition = recorded.partition(ReadBuffer::hash);
		long timestamp = Clock.systemUTC().millis();
		byte[] key = recorded.getKey().toByteArray();
		byte[] value = recorded.getValue().toByteArray();
		// TODO serialize header?
		recorded.getHeader();
		return new ProducerRecord<>(topic.value(), partition, timestamp, key, value);
	}

	private final Scheduler scheduler;
	private final Lazy<Producer<byte[], byte[]>> producer;
	private final LazyListener<KafkaCallback> lazyListener;

	public KafkaChannel(Scheduler scheduler, Supplier<Producer<byte[], byte[]>> producerFactory,
			Supplier<Consumer<byte[], byte[]>> consumerFactory) {
		this.scheduler = Require.nonNull(scheduler);
		this.producer = new Lazy<>(producerFactory::get, Producer::close);
		this.lazyListener = new LazyListener<>(l -> new KafkaCallback(scheduler, consumerFactory.get(), l, l));
	}

	@Override
	public void closeChecked() {
		producer.destroy();
	}

	@Override
	public void send(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed) {
		if (lazyListener.isNotBothAssigned()) {
			producer.get().send(convert(topic, committed));
		}
	}

	@Override
	public ColdChannel.Callback register(ColdChannel.Listener listener) {
		return lazyListener.assign(listener);
	}

	@Override
	public HotChannel.Callback register(HotChannel.Listener listener) {
		return lazyListener.assign(listener);
	}

	@Override
	public Promise<CommitResult<ReadBuffer, ReadBuffer>> trySend(ResourceDescriptor topic, Uncommitted<ReadBuffer, ReadBuffer> attempt) {
		Deferred<CommitResult<ReadBuffer, ReadBuffer>> deferred = scheduler.createDeferredPromise();
		producer.get().send(convert(topic, attempt), (metadata, exception) -> {
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
