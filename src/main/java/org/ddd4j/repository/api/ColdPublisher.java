package org.ddd4j.repository.api;

import org.ddd4j.Throwing;
import org.ddd4j.repository.RepositoryDefinition;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.reactivestreams.Subscriber;

public interface ColdPublisher extends Throwing.Closeable {

	interface Factory {

		Key<Factory> KEY = Key.of(Factory.class);

		ColdPublisher create();
	}

	<K, V> void subscribe(Subscriber<Committed<K, V>> subscriber, RepositoryDefinition<K, V> definition, Revision... startRevisions);
}