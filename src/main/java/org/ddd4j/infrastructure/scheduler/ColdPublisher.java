package org.ddd4j.infrastructure.scheduler;

import java.util.Optional;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.scheduler.ColdSource.Connection;
import org.ddd4j.value.collection.Seq;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class ColdPublisher<T> extends RegisteringPublisher<T> implements Publisher<T> {

	private static class ColdSubscription<T> implements Subscription {

		private final Subscriber<? super T> subscriber;
		private final Connection<T> connection;
		private final Requesting requesting;

		public ColdSubscription(Subscriber<? super T> subscriber, Connection<T> connection) {
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
					connection.close();
				}
			}
		}

		@Override
		public void cancel() {
			connection.close();
		}
	}

	private final Scheduler scheduler;
	private final ColdSource<T> source;

	public ColdPublisher(Scheduler scheduler, ColdSource<T> source) {
		this.scheduler = Require.nonNull(scheduler);
		this.source = Require.nonNull(source);
	}

	private Optional<Connection<T>> openConnection(Subscriber<? super T> subscriber) {
		try {
			return Optional.of(source.open());
		} catch (Exception e) {
			subscriber.onError(e);
			return Optional.empty();
		}
	}

	@Override
	public void subscribe(Subscriber<? super T> subscriber) {
		Require.nonNull(subscriber);
		openConnection(subscriber).map(c -> new ColdSubscription<>(subscriber, c)).ifPresent(subscriber::onSubscribe);
	}

	@Override
	protected Subscription subscribeNew(Subscriber<? super T> subscriber) {
		openConnection(subscriber).map(c -> new ColdSubscription<>(subscriber, c)).ifPresent(subscriber::onSubscribe);
	}
}