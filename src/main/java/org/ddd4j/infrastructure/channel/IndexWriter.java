package org.ddd4j.infrastructure.channel;

import org.ddd4j.repository.RepositoryDefinition;
import org.ddd4j.spi.Key;
import org.ddd4j.value.Value;
import org.ddd4j.value.indexed.Indexed;
import org.ddd4j.value.indexed.Indexer;

public interface IndexWriter<K, V> extends Indexed, Writer<K, V> {

	interface Factory extends DataAccessFactory {

		<K extends Value<K>, V> IndexWriter<K, V> create(RepositoryDefinition<K, V> definition, Indexer<? super V> indexer);
	}

	Key<Factory> FACTORY = Key.of(Factory.class);
}