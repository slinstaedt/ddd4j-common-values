package org.ddd4j.infrastructure.queue.disruptor;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.queue.Queue;
import org.ddd4j.infrastructure.queue.QueueFactory;
import org.ddd4j.value.collection.Configuration;

public class DisruptorQueueFactory implements QueueFactory {

	private final Configuration configuration;

	public DisruptorQueueFactory(Configuration configuration) {
		this.configuration = Require.nonNull(configuration);
	}

	@Override
	public <E> Queue<E> create(int bufferSize) {
		return new DisruptorQueue<>(bufferSize);
	}

	@Override
	public Configuration getConfiguration() {
		return configuration;
	}
}