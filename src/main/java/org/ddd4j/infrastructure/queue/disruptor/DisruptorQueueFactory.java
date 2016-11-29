package org.ddd4j.infrastructure.queue.disruptor;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.queue.Queue;
import org.ddd4j.infrastructure.queue.QueueFactory;
import org.ddd4j.spi.Configuration;

public class DisruptorQueueFactory implements QueueFactory {

	private final Configuration configuration;

	public DisruptorQueueFactory(Configuration configuration) {
		this.configuration = Require.nonNull(configuration);
	}

	@Override
	public <E> Queue<E> create() {
		return new DisruptorQueue<>(configuration.get(BUFFER_SIZE));
	}

	@Override
	public Configuration getConfiguration() {
		return configuration;
	}
}