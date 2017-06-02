package org.ddd4j.repository;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Uncommitted;

public interface Repository {

	interface Reader<K, V> {

		Promise<Committed<K, V>> get(K key);
	}

	interface Writer<K, V> {

		Promise<CommitResult<K, V>> put(Uncommitted<K, V> attempt);
	}

	interface RepositoryDefinition<K, V> {

		ResourceDescriptor name();
	}

	Key<Repository> KEY = Key.of(Repository.class);

	<K, V> Reader<K, V> reader(RepositoryDefinition<K, V> definition);

	<K, V> Writer<K, V> writer(RepositoryDefinition<K, V> definition);
}
