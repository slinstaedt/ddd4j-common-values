package org.ddd4j.infrastructure.channel;

import org.ddd4j.Throwing;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.reactivestreams.Subscriber;

public interface ColdPublisher<K, V> {

	interface Factory extends Throwing.Closeable {

		ColdPublisher<ReadBuffer, ReadBuffer> create();
	}

	Key<Factory> FACTORY = Key.of(Factory.class);

	void subscribe(Subscriber<? super Committed<K, V>> subscriber, ResourceDescriptor descriptor, Revision... startRevisions);
}