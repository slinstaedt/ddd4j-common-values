package org.ddd4j.infrastructure.channel;

import java.util.stream.IntStream;

import org.ddd4j.Throwing;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Committed;
import org.reactivestreams.Subscriber;

public interface HotPublisher<K, V> extends org.reactivestreams.Publisher<Committed<K, V>> {

	/**
	 * Listener for partition rebalance events.
	 */
	interface Listener {

		Listener IGNORE = new Listener() {

			@Override
			public Promise<Void> onPartitionsAssigned(IntStream partitions) {
				return Promise.completed();
			}

			@Override
			public Promise<Void> onPartitionsRevoked(IntStream partitions) {
				return Promise.completed();
			}
		};

		Promise<Void> onPartitionsAssigned(IntStream partitions);

		Promise<Void> onPartitionsRevoked(IntStream partitions);
	}

	interface Factory extends Throwing.Closeable {

		Key<Factory> KEY = Key.of(Factory.class);

		HotPublisher<ReadBuffer, ReadBuffer> create(ResourceDescriptor descriptor);
	}

	@Override
	default void subscribe(Subscriber<? super Committed<K, V>> subscriber) {
		subscribe(subscriber, Listener.IGNORE);
	}

	void subscribe(Subscriber<? super Committed<K, V>> subscriber, Listener listener);
}