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

	interface Factory extends DataAccessFactory {

		HotSource createHotSource(Listener<ReadBuffer, ReadBuffer> listener);
	}

	interface Listener<K, V> {

		void onError(Throwable throwable);

		void onNext(ResourceDescriptor resource, Committed<K, V> committed);

		void onPartitionsAssigned(Seq<ResourcePartition> partitions);

		void onPartitionsRevoked(Seq<ResourcePartition> partitions);

		default void onSubscribed(int partitionCount) {
		}
	}

	Key<Factory> FACTORY = Key.of(Factory.class);

	Promise<Integer> subscribe(ResourceDescriptor resource);

	void unsubscribe(ResourceDescriptor resource);
}