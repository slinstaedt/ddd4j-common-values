package org.ddd4j.repository;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.value.Type;
import org.ddd4j.value.Type.Variable;
import org.ddd4j.value.Value;

public interface RepositoryDefinition<K extends Value<K>, V> {

	ResourceDescriptor getResource();

	default K deserializeKey(ReadBuffer buffer) {
		return getKeyType().constructor(Type.of(ReadBuffer.class)).evaluate(buffer);
	}

	default Type<K> getKeyType() {
		Variable<RepositoryDefinition<K, V>, K> var = Type.variable(RepositoryDefinition.class, 0, Value.class);
		return Type.ofInstance(this).resolve(var);
	}

	default Type<V> getValueType() {
		Variable<RepositoryDefinition<K, V>, V> var = Type.variable(RepositoryDefinition.class, 1, Object.class);
		return Type.ofInstance(this).resolve(var);
	}

	default void serializeKey(K key, WriteBuffer buffer) {
		key.serialize(buffer);
	}
}