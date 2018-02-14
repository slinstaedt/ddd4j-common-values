package org.ddd4j.infrastructure.channel.kafka;

import org.apache.kafka.clients.producer.Producer;
import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.spi.Writer;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed.Published;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revision;

public class KafkaWriter implements Writer<ReadBuffer, ReadBuffer> {

	private final Scheduler scheduler;
	private final Producer<byte[], byte[]> client;
	private final ChannelName name;

	public KafkaWriter(Scheduler scheduler, Producer<byte[], byte[]> client, ChannelName name) {
		this.scheduler = Require.nonNull(scheduler);
		this.client = Require.nonNull(client);
		this.name = Require.nonNull(name);
	}

	@Override
	public Promise<Published<ReadBuffer, ReadBuffer>> put(Recorded<ReadBuffer, ReadBuffer> recorded) {
		Promise.Deferred<Published<ReadBuffer, ReadBuffer>> deferred = scheduler.createDeferredPromise();
		client.send(KafkaChannelFactory.convert(name, recorded), (metadata, exception) -> {
			if (metadata != null) {
				Revision nextExpected = new Revision(metadata.partition(), metadata.offset() + 1);
				deferred.completeSuccessfully(recorded.committed(nextExpected).published());
			} else {
				deferred.completeExceptionally(exception);
			}
		});
		return deferred;
	}
}
