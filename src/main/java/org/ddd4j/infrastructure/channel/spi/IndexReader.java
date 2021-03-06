package org.ddd4j.infrastructure.channel.spi;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.domain.value.ChannelSpec;
import org.ddd4j.spi.Ref;
import org.ddd4j.util.value.Sequence;
import org.ddd4j.value.indexed.Indexed;
import org.ddd4j.value.indexed.Indexer;
import org.ddd4j.value.indexed.Query;
import org.ddd4j.value.versioned.Committed;

public interface IndexReader<K, V> extends Indexed, Reader<K, V> {

	interface Factory extends DataAccessFactory {

		<K, V> IndexReader<K, V> create(ChannelSpec<K, V> spec, Indexer<? super V> indexer);
	}

	Ref<Factory> FACTORY = Ref.of(Factory.class);

	Promise<Sequence<Committed<K, V>>> perform(Query<V> query);
}