package org.ddd4j.repository.capability;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.repository.RepositoryDefinition;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Committed;

public interface Reader<K, V> {

	interface Factory {

		Key<Factory> KEY = Key.of(Factory.class);

		<K, V> Reader<K, V> create(RepositoryDefinition<K, V> definition);
	}

	Promise<Committed<K, V>> get(K key);
}