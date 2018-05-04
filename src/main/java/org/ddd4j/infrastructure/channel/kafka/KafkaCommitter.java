package org.ddd4j.infrastructure.channel.kafka;

import org.apache.kafka.clients.producer.Producer;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.spi.Committer;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Uncommitted;

public class KafkaCommitter implements Committer<ReadBuffer, ReadBuffer> {

	private final Scheduler scheduler;
	private final Producer<byte[], byte[]> client;
	private final ChannelName name;

	public KafkaCommitter(Scheduler scheduler, Producer<byte[], byte[]> client, ChannelName name) {
		this.scheduler = Require.nonNull(scheduler);
		this.client = Require.nonNull(client);
		this.name = Require.nonNull(name);
	}

	@Override
	public Promise<? extends CommitResult<ReadBuffer, ReadBuffer>> commit(Uncommitted<ReadBuffer, ReadBuffer> attempt) {
		Promise.Deferred<CommitResult<ReadBuffer, ReadBuffer>> deferred = scheduler.createDeferredPromise();
		client.send(KafkaChannelFactory.convert(name, attempt), (metadata, exception) -> {
			if (metadata != null) {
				Revision nextExpected = new Revision(metadata.partition(), metadata.offset() + 1);
				deferred.completeSuccessfully(attempt.committed(nextExpected));
			} else {
				deferred.completeExceptionally(exception);
			}
		});
		return deferred;
	}
}
