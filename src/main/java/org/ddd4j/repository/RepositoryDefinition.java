package org.ddd4j.repository;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.WriteBuffer;

public interface RepositoryDefinition<K, V> {

	ResourceDescriptor getDescriptor();

	void serializeKey(WriteBuffer buffer, K key);
}