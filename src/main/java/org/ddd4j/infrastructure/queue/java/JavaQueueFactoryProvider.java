package org.ddd4j.infrastructure.queue.java;

import org.ddd4j.infrastructure.queue.QueueFactory;
import org.ddd4j.infrastructure.queue.QueueFactoryProvider;
import org.ddd4j.spi.Configuration;
import org.ddd4j.spi.ServiceLocator;

public class JavaQueueFactoryProvider implements QueueFactoryProvider {

	@Override
	public QueueFactory provideService(Configuration configuration, ServiceLocator locator) {
		return new JavaQueueFactory(configuration);
	}
}
