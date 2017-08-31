package org.ddd4j.infrastructure.channel.kafka;

import org.apache.kafka.clients.producer.Producer;
import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.Writer;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.infrastructure.channel.domain.ChannelPartition;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Recorded;

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
	public Promise<ChannelPartition> put(Recorded<ReadBuffer, ReadBuffer> recorded) {
		Promise.Deferred<ChannelPartition> deferred = scheduler.createDeferredPromise();
		client.send(KafkaChannelFactory.convert(name, recorded), (metadata, exception) -> {
			if (metadata != null) {
				ChannelPartition partition = new ChannelPartition(name, metadata.partition());
				deferred.completeSuccessfully(partition);
			} else {
				deferred.completeExceptionally(exception);
			}
		});
		return deferred;
	}
}
