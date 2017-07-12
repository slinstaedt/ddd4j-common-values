package org.ddd4j.infrastructure.channel;

import org.ddd4j.Throwing;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.ResourcePartition;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Key;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Committed;

public interface HotSource extends Throwing.Closeable {

	interface Callback {

		void onError(Throwable throwable);

		void onPartitionsAssigned(Seq<ResourcePartition> partitions);

		void onPartitionsRevoked(Seq<ResourcePartition> partitions);

		default void onSubscribed(int partitionCount) {
		}
	}

	interface Factory extends DataAccessFactory {

		HotSource createHotSource(Callback callback);
	}

	interface Listener<K, V> {

		void onNext(ResourceDescriptor resource, Committed<K, V> committed);
	}

	Key<Factory> FACTORY = Key.of(Factory.class);

	Promise<Integer> subscribe(Listener<ReadBuffer, ReadBuffer> listener, ResourceDescriptor resource);

	default void unsubscribe(Listener<ReadBuffer, ReadBuffer> listener) {
		unsubscribe(listener, null);
	}

	void unsubscribe(Listener<ReadBuffer, ReadBuffer> listener, ResourceDescriptor resource);
}