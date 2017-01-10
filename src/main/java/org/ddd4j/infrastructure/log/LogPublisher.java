package org.ddd4j.infrastructure.log;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.buffer.Bytes;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revisions;

public interface LogPublisher {

	interface Subscriber<K, V> {

		void onCommitted(Committed<K, V[]> committed);

		Revisions loadRevisions();

		void saveRevisions(Revisions revisions);
	}

	void subscribe(ResourceDescriptor topic, Subscriber<Bytes, Bytes> subscriber);

	void unsubscribe(Subscriber<Bytes, Bytes> subscriber);
}
