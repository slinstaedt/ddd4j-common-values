package org.ddd4j.infrastructure.channel.spi;

import org.ddd4j.infrastructure.domain.value.ChannelSpec;
import org.ddd4j.spi.Ref;
import org.ddd4j.value.indexed.Indexed;
import org.ddd4j.value.indexed.Indexer;

public interface IndexWriter<K, V> extends Indexed, Writer<K, V> {

	interface Factory extends DataAccessFactory {

		<K, V> IndexWriter<K, V> create(ChannelSpec<K, V> spec, Indexer<? super V> indexer);
	}

	Ref<Factory> FACTORY = Ref.of(Factory.class);
}