package org.ddd4j.repository.capability;

import org.ddd4j.repository.RepositoryDefinition;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Committed;

public interface Publisher<K, V> extends org.reactivestreams.Publisher<Committed<K, V>> {

	interface Factory {

		Key<Factory> KEY = Key.of(Factory.class);

		<K, V> Publisher<K, V> create(RepositoryDefinition<K, V> definition);
	}
}