package org.ddd4j.repository.api;

import org.ddd4j.Throwing;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Committed;

public interface Reader<K, V> {

	interface Factory extends Throwing.Closeable {

		Key<Factory> KEY = Key.of(Factory.class);

		Reader<ReadBuffer, ReadBuffer> create(ResourceDescriptor descriptor);
	}

	Promise<Committed<K, V>> get(K key);
}