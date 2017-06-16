package org.ddd4j.repository.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.ddd4j.Require;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.repository.api.HotPublisher;
import org.ddd4j.value.versioned.Committed;
import org.reactivestreams.Subscriber;

public class KafkaHotPublisher implements HotPublisher<ReadBuffer, ReadBuffer> {

	static class Factory {

		private Agent<Consumer<byte[], byte[]>> client;

		public HotPublisher<ReadBuffer, ReadBuffer> create(ResourceDescriptor topic) {
			return new KafkaHotPublisher(topic, client);
		}
	}

	private final ResourceDescriptor topic;
	private final Agent<Consumer<byte[], byte[]>> client;

	public KafkaHotPublisher(ResourceDescriptor topic, Agent<Consumer<byte[], byte[]>> client) {
		this.topic = Require.nonNull(topic);
		this.client = Require.nonNull(client);
	}

	@Override
	public void subscribe(Subscriber<? super Committed<ReadBuffer, ReadBuffer>> subscriber, Listener listener) {
		// TODO Auto-generated method stub

	}
}
