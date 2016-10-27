package org.ddd4j.stream;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.ddd4j.contract.Require;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class BufferedPublisher<T> implements Publisher<T> {

	private final Set<Subscriber<? super T>> subscribers;

	public BufferedPublisher() {
		this.subscribers = new CopyOnWriteArraySet<>();
	}

	@Override
	public void subscribe(Subscriber<? super T> subscriber) {
		if (subscribers.add(Require.nonNull(subscriber))) {
			subscriber.onSubscribe(new Subscription() {

				@Override
				public void request(long n) {
					// TODO
				}

				@Override
				public void cancel() {
					subscribers.remove(subscriber);
				}
			});
		} else {
			subscriber.onError(new IllegalStateException("Already subscribed"));
		}
	}
}
