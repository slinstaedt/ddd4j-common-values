package org.ddd4j.infrastructure.channel;

import org.ddd4j.Throwing;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.ResourcePartition;
import org.ddd4j.infrastructure.channel.util.Listener;
import org.ddd4j.infrastructure.channel.util.Listeners;
import org.ddd4j.infrastructure.channel.util.Subscriptions;
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

		HotSource createHotSource(Callback callback, Listener<ReadBuffer, ReadBuffer> listener);

		default Publisher createHotPublisher(Callback callback) {
			return new Publisher(this, callback);
		}
	}

	class Publisher {

		private final HotSource source;
		private final Subscriptions subscriptions;

		public Publisher(Factory factory, Callback callback) {
			this.source = factory.createHotSource(callback, this::onNext);
			this.subscriptions = new Subscriptions(this::onSubscribe);
		}

		private void onNext(ResourceDescriptor resource, Committed<ReadBuffer, ReadBuffer> committed) {
			subscriptions.onNext(resource, committed);
		}

		private Listeners onSubscribe(ResourceDescriptor resource) {
			return new Listeners(resource, source.subscribe(resource), () -> source.unsubscribe(resource));
		}

		public Promise<Integer> subscribe(ResourceDescriptor resource, Listener<ReadBuffer, ReadBuffer> listener) {
			return subscriptions.subscribe(resource, listener);
		}

		public void unsubscribe(ResourceDescriptor resource, Listener<?, ?> listener) {
			subscriptions.unsubscribe(resource, listener);
		}
	}

	Key<Factory> FACTORY = Key.of(Factory.class);

	Promise<Integer> subscribe(ResourceDescriptor resource);

	void unsubscribe(ResourceDescriptor resource);
}