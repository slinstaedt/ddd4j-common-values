package org.ddd4j.infrastructure.channel;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.ddd4j.Require;
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

		void onPartitionsAssigned(Seq<ResourcePartition> partitions);

		void onPartitionsRevoked(Seq<ResourcePartition> partitions);

		default void onSubscribed(int partitionCount) {
		}

		void onNext(ResourceDescriptor resource, Committed<K, V> committed);
	}

	class Listeners {

		private final ResourceDescriptor resource;
		private final Promise<Integer> partitionSize;
		private final Runnable closer;
		private final Set<Listener<ReadBuffer, ReadBuffer>> listeners;

		public Listeners(ResourceDescriptor resource, Promise<Integer> partitionSize, Runnable closer) {
			this.resource = Require.nonNull(resource);
			this.partitionSize = Require.nonNull(partitionSize);
			this.closer = Require.nonNull(closer);
			this.listeners = Collections.newSetFromMap(new ConcurrentHashMap<>());
		}

		public Listeners(String resource, Promise<Integer> partitionSize) {
			this(ResourceDescriptor.of(resource), partitionSize, Void.class::hashCode);
		}

		public Listeners(String resource, Promise<Integer> partitionSize, Runnable closer) {
			this(ResourceDescriptor.of(resource), partitionSize, closer);
		}

		public Listeners add(Listener<ReadBuffer, ReadBuffer> listener) {
			listeners.add(Require.nonNull(listener));
			return this;
		}

		public void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
			listeners.forEach(l -> l.onNext(resource, DataAccessFactory.resetBuffers(committed)));
		}

		public Promise<Integer> partitionSize() {
			return partitionSize;
		}

		public Listeners remove(Listener<ReadBuffer, ReadBuffer> listener) {
			listeners.remove(listener);
			if (listeners.isEmpty()) {
				closer.run();
				return null;
			} else {
				return this;
			}
		}
	}

	class Subscriptions {

		private static final Listeners NONE = new Listeners("<NONE>", Promise.failed(new AssertionError()));

		private final Function<String, Listeners> onSubscribe;
		private final ConcurrentMap<String, Listeners> listeners;

		public Subscriptions(Function<String, Listeners> onSubscribe) {
			this.onSubscribe = Require.nonNull(onSubscribe);
			this.listeners = new ConcurrentHashMap<>();
		}

		public boolean isEmpty() {
			return listeners.isEmpty();
		}

		public void onNext(String resource, Committed<ReadBuffer, ReadBuffer> committed) {
			listeners.getOrDefault(resource, NONE).onNext(committed);
		}

		public Promise<Integer> subscribe(Listener<ReadBuffer, ReadBuffer> listener, ResourceDescriptor resource) {
			return listeners.computeIfAbsent(resource.value(), onSubscribe).partitionSize();
		}

		public Set<String> resources() {
			return Collections.unmodifiableSet(listeners.keySet());
		}

		public void unsubscribe(Listener<ReadBuffer, ReadBuffer> listener, ResourceDescriptor resource) {
			listeners.computeIfPresent(resource.value(), (r, s) -> s.remove(listener));
		}
	}

	Key<Factory> FACTORY = Key.of(Factory.class);

	Promise<Integer> subscribe(ResourceDescriptor resource);

	void unsubscribe(ResourceDescriptor resource);
}