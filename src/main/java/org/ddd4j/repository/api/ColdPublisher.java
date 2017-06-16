package org.ddd4j.repository.api;

import java.util.stream.Stream;

import org.ddd4j.repository.RepositoryDefinition;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.reactivestreams.Subscriber;

public interface ColdPublisher<K, V> {

	interface Factory {

		Key<Factory> KEY = Key.of(Factory.class);

		<K, V> ColdPublisher<K, V> create(RepositoryDefinition<K, V> definition);
	}

	void subscribe(Subscriber<? super Committed<K, V>> subscriber, Stream<Revision> startRevisions);
}