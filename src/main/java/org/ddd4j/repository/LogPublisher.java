package org.ddd4j.repository;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.Throwing.Closeable;
import org.ddd4j.Throwing.TConsumer;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.ResourcePartition;
import org.ddd4j.infrastructure.channel.ColdSource;
import org.ddd4j.infrastructure.channel.DataAccessFactory;
import org.ddd4j.infrastructure.channel.HotSource;
import org.ddd4j.infrastructure.channel.HotSource.Listener;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.log.Requesting;
import org.ddd4j.spi.Key;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class LogPublisher<K, V> implements org.reactivestreams.Publisher<Committed<K, V>> {

	private static class Listeners {

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

	private static class SubscriptionsXXX {

		private static final Listeners NONE = new Listeners("<NONE>", Promise.failed(new AssertionError()));

		private final Function<String, Listeners> onSubscribe;
		private final ConcurrentMap<String, Listeners> listeners;

		public SubscriptionsXXX(Function<String, Listeners> onSubscribe) {
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

	private static class Subscriptions {

		private final SubscriptionListener listener;
		private final Map<ResourceDescriptor, Promise<Revisions>> hotRevisions;

		Subscriptions(SubscriptionListener listener) {
			this.listener = Require.nonNull(listener);
			this.hotRevisions = new ConcurrentHashMap<>();
		}

		void closeCallbacksAndDelegate(Closeable otherCallback, Throwable throwable) {
			if (otherCallback != null) {
				otherCallback.close();
			}
			hotRevisions.clear();
			listener.onError(throwable);
		}

		void onNextIfSubscribed(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed, TConsumer<Revisions> action) {
			Promise<Revisions> promise = hotRevisions.get(topic);
			if (promise != null) {
				promise.whenCompleteSuccessfully(action);
				listener.onNext(topic, committed);
			}
		}

		void onPartitionsAssigned(ResourceDescriptor topic, int[] partitions) {
			listener.onPartitionsAssigned(topic, partitions);
		}

		void onPartitionsRevoked(ResourceDescriptor topic, int[] partitions) {
			listener.onPartitionsRevoked(topic, partitions);
		}

		Promise<Integer> subscribeIfNeeded(ResourceDescriptor topic, Function<ResourceDescriptor, Promise<Revisions>> subscriber) {
			return hotRevisions.computeIfAbsent(topic, subscriber).thenApply(Revisions::getPartitionSize);
		}

		void unsubscribeIfNeeded(ResourceDescriptor topic, Consumer<ResourceDescriptor> unsubscriber) {
			if (hotRevisions.remove(topic) != null) {
				unsubscriber.accept(topic);
			}
		}
	}

	public interface RevisionCallback {

		Promise<Revision[]> loadRevisions(int[] partitions);

		Promise<Void> saveRevisions(Revision[] revisions);
	}

	private class SubscriptionListener implements Subscription, HotSource.Listener<K, V> {

		private final Subscriber<? super Committed<K, V>> subscriber;
		private final RevisionCallback callback;
		private final Requesting requesting;

		public SubscriptionListener(Subscriber<? super Committed<K, V>> subscriber, RevisionCallback callback) {
			this.subscriber = Require.nonNull(subscriber);
			this.callback = Require.nonNull(callback);
			this.requesting = new Requesting();
		}

		@Override
		public void request(long n) {
			requesting.more(n);
		}

		@Override
		public void cancel() {
			cancelSubscription(subscriber);
		}
	}

	private class CallBack implements HotSource.Callback {

		@Override
		public void onError(Throwable throwable) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onPartitionsAssigned(Seq<ResourcePartition> partitions) {
			callback.loadRevisions(partitions).whenCompleteSuccessfully(r -> cold.subscribe(subscriber, descriptor, r));
		}

		@Override
		public void onPartitionsRevoked(Seq<ResourcePartition> partitions) {
			// TODO Auto-generated method stub

		}
	}

	interface Factory {

		Key<Factory> KEY = Key.of(Factory.class);

		LogPublisher<ReadBuffer, ReadBuffer> create(ResourceDescriptor descriptor);
	}

	private ResourceDescriptor descriptor;
	private ColdSource.Factory cold;
	private HotSource hot;
	private Map<Subscriber<? super Committed<K, V>>, SubscriptionListener> subscriptions;

	public LogPublisher(HotSource.Factory hot) {
	}

	@Override
	public void subscribe(Subscriber<? super Committed<K, V>> subscriber) {
		subscribe(subscriber, null); // TODO
	}

	public void subscribe(Subscriber<? super Committed<K, V>> subscriber, RevisionCallback callback) {
		subscriptions.computeIfAbsent(subscriber, s -> {
			SubscriptionListener subscription = new SubscriptionListener(s, callback);
			hot.subscribe(subscriber, subscription);
			s.onSubscribe(subscription);
			return subscription;
		});
	}

	private boolean cancelSubscription(Subscriber<? super Committed<K, V>> subscriber) {
		return subscriptions.remove(subscriber) != null;
	}
}