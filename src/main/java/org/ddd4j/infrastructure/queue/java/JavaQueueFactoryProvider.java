package org.ddd4j.infrastructure.queue.java;

import org.ddd4j.infrastructure.queue.QueueFactory;
import org.ddd4j.infrastructure.queue.QueueFactoryProvider;
import org.ddd4j.spi.ServiceLocator;
import org.ddd4j.value.collection.Configuration;

public class JavaQueueFactoryProvider implements QueueFactoryProvider {

	@Override
	public QueueFactory provideService(Configuration configuration, ServiceLocator locator) {
		return new JavaQueueFactory(configuration);
	}
}
