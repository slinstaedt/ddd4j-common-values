package org.ddd4j.repository;

import java.util.Map;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ColdPublisher;
import org.ddd4j.infrastructure.channel.HotPublisher;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.log.Requesting;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class Publisher<K, V> implements org.reactivestreams.Publisher<Committed<K, V>> {

	public interface RevisionCallback {

		Promise<Revision[]> loadRevisions(int[] partitions);

		Promise<Void> saveRevisions(Revision[] revisions);
	}

	private class Listener implements Subscription, HotPublisher.Listener {

		private final Subscriber<? super Committed<K, V>> subscriber;
		private final RevisionCallback callback;
		private final Requesting requesting;

		public Listener(Subscriber<? super Committed<K, V>> subscriber, RevisionCallback callback) {
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

		@Override
		public void onError(Throwable throwable) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onNext(ResourceDescriptor resource, Committed<ReadBuffer, ReadBuffer> committed) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onPartitionsAssigned(ResourceDescriptor resource, int[] partitions) {
			callback.loadRevisions(partitions).whenCompleteSuccessfully(r -> cold.subscribe(subscriber, descriptor, r));
		}

		@Override
		public void onPartitionsRevoked(ResourceDescriptor resource, int[] partitions) {
			// TODO Auto-generated method stub

		}
	}

	interface Factory {

		Key<Factory> KEY = Key.of(Factory.class);

		Publisher<ReadBuffer, ReadBuffer> create(ResourceDescriptor descriptor);
	}

	private ResourceDescriptor descriptor;
	private ColdPublisher<K, V> cold;
	private HotPublisher<K, V> hot;
	private Map<Subscriber<? super Committed<K, V>>, Listener> subscriptions;

	public Publisher() {
	}

	@Override
	public void subscribe(Subscriber<? super Committed<K, V>> subscriber) {
		subscribe(subscriber, null); // TODO
	}

	public void subscribe(Subscriber<? super Committed<K, V>> subscriber, RevisionCallback callback) {
		subscriptions.computeIfAbsent(subscriber, s -> {
			Listener subscription = new Listener(s, callback);
			hot.subscribe(subscriber, subscription);
			s.onSubscribe(subscription);
			return subscription;
		});
	}

	private boolean cancelSubscription(Subscriber<? super Committed<K, V>> subscriber) {
		return subscriptions.remove(subscriber) != null;
	}
}