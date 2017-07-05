package org.ddd4j.repository;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.value.Typed;
import org.ddd4j.value.Value;

public interface RepositoryDefinition<K extends Value<K>, V> extends Typed<V> {

	ResourceDescriptor getDescriptor();

	default void serializeKey(K key, WriteBuffer buffer) {
		key.serialize(buffer);
	}
}