package org.ddd4j.repository.capability;

import org.ddd4j.log.RevisionsCallback;
import org.ddd4j.repository.RepositoryDefinition;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Committed;
import org.reactivestreams.Subscriber;

public interface Streamer<K, V> extends Publisher<K, V> {

	interface Factory {

		Key<Factory> KEY = Key.of(Factory.class);

		<K, V> Streamer<K, V> create(RepositoryDefinition<K, V> definition);
	}

	void subscribe(Subscriber<Committed<K, V>> subscriber, RevisionsCallback callback);
}