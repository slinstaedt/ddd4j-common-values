package org.ddd4j.repository.capability;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.repository.RepositoryDefinition;
import org.ddd4j.spi.Key;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.indexed.Indexed;
import org.ddd4j.value.indexed.Indexer;
import org.ddd4j.value.indexed.Query;
import org.ddd4j.value.versioned.Committed;

public interface IndexReader<K, V> extends Indexed, Reader<K, V> {

	interface Factory {

		Key<Factory> KEY = Key.of(Factory.class);

		<K, V> IndexReader<K, V> create(RepositoryDefinition<K, V> definition, Indexer<? super V> indexer);
	}

	Promise<Seq<Committed<K, V>>> perform(Query<V> query);
}