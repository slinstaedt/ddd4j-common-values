package org.ddd4j.repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.Throwing.Closeable;
import org.ddd4j.Throwing.TConsumer;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.ResourcePartition;
import org.ddd4j.infrastructure.channel.ColdSource;
import org.ddd4j.infrastructure.channel.HotSource;
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

	private class SubscriptionListener implements Subscription, HotSource.SourceListener<K, V> {

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