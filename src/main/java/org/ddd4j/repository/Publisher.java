package org.ddd4j.repository;

import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.ColdPublisher;
import org.ddd4j.infrastructure.channel.HotPublisher;
import org.ddd4j.log.Requesting;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class Publisher<K, V> implements HotPublisher.Listener {

	interface Callback {

		Promise<Stream<Revision>> loadRevisions(IntStream partitions);

		Promise<Void> saveRevisions(Stream<Revision> revisions);
	}

	private class PublisherSubscription implements Subscription {

		private final Subscriber<? super Committed<K, V>> subscriber;
		private final Callback callback;
		private final Requesting requesting;

		public PublisherSubscription(Subscriber<? super Committed<K, V>> subscriber, Callback callback) {
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

	interface Factory {

		Key<Factory> KEY = Key.of(Factory.class);

		<K, V> Publisher<K, V> create(RepositoryDefinition<K, V> definition);
	}

	private ColdPublisher<K, V> cold;
	private HotPublisher<K, V> hot;
	private Map<Subscriber<? super Committed<K, V>>, ?> subscriptions;

	public void subscribe(Subscriber<? super Committed<K, V>> subscriber, Callback callback) {
		subscriptions.putIfAbsent(subscriber, new PublisherSubscription(subscriber, callback));
		hot.subscribe(subscriber, this);
	}

	private void cancelSubscription(Subscriber<? super Committed<K, V>> subscriber) {

	}

	@Override
	public Promise<Void> onPartitionsAssigned(IntStream partitions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Promise<Void> onPartitionsRevoked(IntStream partitions) {
		// TODO Auto-generated method stub
		return null;
	}
}