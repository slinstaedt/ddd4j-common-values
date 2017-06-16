package org.ddd4j.repository.api;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.repository.RepositoryDefinition;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Uncommitted;

public interface Committer<K, V> {

	interface Factory {

		Key<Factory> KEY = Key.of(Factory.class);

		<K, V> Committer<K, V> create(RepositoryDefinition<K, V> definition);
	}

	Promise<? extends CommitResult<K, V>> commit(Uncommitted<K, V> attempt);
}