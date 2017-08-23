package org.ddd4j.infrastructure.channel;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.domain.ChannelSpec;
import org.ddd4j.spi.Key;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.indexed.Indexed;
import org.ddd4j.value.indexed.Indexer;
import org.ddd4j.value.indexed.Query;
import org.ddd4j.value.versioned.Committed;

public interface IndexReader<K, V> extends Indexed, Reader<K, V> {

	interface Factory extends DataAccessFactory {

		<K, V> IndexReader<K, V> create(ChannelSpec<K, V> spec, Indexer<? super V> indexer);
	}

	Key<Factory> FACTORY = Key.of(Factory.class);

	Promise<Seq<Committed<K, V>>> perform(Query<V> query);
}