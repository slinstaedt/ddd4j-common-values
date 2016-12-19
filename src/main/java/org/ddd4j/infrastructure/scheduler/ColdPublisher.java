package org.ddd4j.infrastructure.scheduler;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.scheduler.ColdSource.Connection;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.collection.Seq;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class ColdPublisher<T> extends RegisteringPublisher<T> implements Publisher<T> {

	private class ColdSubscription implements Subscription {

		private final Subscriber<? super T> subscriber;
		private final Connection<T> connection;
		private final Requesting requesting;

		ColdSubscription(Subscriber<? super T> subscriber, Connection<T> connection) {
			this.subscriber = Require.nonNull(subscriber);
			this.connection = Require.nonNull(connection);
			this.requesting = new Requesting();
		}

		@Override
		public void request(long n) {
			try {
				Seq<? extends T> result = connection.request(requesting.more(n).asInt());
				if (result.isEmpty()) {
					subscriber.onComplete();
				} else {
					result.forEach(subscriber::onNext);
				}
			} catch (Exception e) {
				try {
					subscriber.onError(e);
				} finally {
					cancel();
				}
			}
		}

		@Override
		public void cancel() {
			if (unsubscribe(subscriber)) {
				connection.close();
			}
		}
	}

	private final ColdSource<T> source;

	public ColdPublisher(ColdSource<T> source) {
		this.source = Require.nonNull(source);
	}

	private Connection<T> openConnection(Subscriber<? super T> subscriber) {
		try {
			return source.open();
		} catch (Exception e) {
			subscriber.onError(e);
			return Throwing.unchecked(e);
		}
	}

	@Override
	protected Subscription subscribeNew(Subscriber<? super T> subscriber) {
		Connection<T> connection = openConnection(subscriber);
		ColdSubscription subscription = new ColdSubscription(subscriber, connection);
		subscriber.onSubscribe(subscription);
		return subscription;
	}
}