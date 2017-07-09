package org.ddd4j.infrastructure.channel;

import org.ddd4j.Throwing;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Committed;

public interface HotPublisher extends Throwing.Closeable {

	/**
	 * Listener for partition rebalance events.
	 */
	interface Listener {

		void onError(Throwable throwable);

		void onNext(ResourceDescriptor resource, Committed<ReadBuffer, ReadBuffer> committed);

		void onPartitionsAssigned(ResourceDescriptor resource, int[] partitions);

		void onPartitionsRevoked(ResourceDescriptor resource, int[] partitions);
	}

	interface Factory extends Throwing.Closeable {

		HotPublisher register(Listener listener);
	}

	Key<Factory> FACTORY = Key.of(Factory.class);

	Promise<Integer> subscribe(ResourceDescriptor resource);

	void unsubscribe(ResourceDescriptor resource);
}