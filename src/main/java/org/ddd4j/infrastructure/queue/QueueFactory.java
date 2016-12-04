package org.ddd4j.infrastructure.queue;

import org.ddd4j.spi.Configuration;
import org.ddd4j.spi.Service;

public interface QueueFactory extends Service<QueueFactory, QueueFactoryProvider> {

	Configuration.Key<Integer> BUFFER_SIZE = Configuration.keyOfInteger("buffer.size", 1024);

	default <E> Queue<E> create() {
		return create(getConfiguration().get(BUFFER_SIZE));
	}

	<E> Queue<E> create(int bufferSize);
}