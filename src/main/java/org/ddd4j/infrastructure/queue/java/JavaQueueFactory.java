package org.ddd4j.infrastructure.queue.java;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.queue.Queue;
import org.ddd4j.infrastructure.queue.QueueFactory;
import org.ddd4j.spi.Configuration;

public class JavaQueueFactory implements QueueFactory {

	private final Configuration configuration;

	public JavaQueueFactory(Configuration configuration) {
		this.configuration = Require.nonNull(configuration);
	}

	@Override
	public <E> Queue<E> create() {
		return new JavaQueue<>(configuration.get(BUFFER_SIZE));
	}

	@Override
	public Configuration getConfiguration() {
		return configuration;
	}
}