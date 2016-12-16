package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.ddd4j.contract.Require;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class BufferedPublisher<T> implements Publisher<T> {

	private class BufferedSubscriber implements Subscriber<T> {

		private class BufferedSubscription implements Subscription {

			private final Subscription delegate;
			private final Requesting requesting;

			public BufferedSubscription(Subscription delegate) {
				this.delegate = Require.nonNull(delegate);
				this.requesting = new Requesting(capacity);
			}

			@Override
			public void request(long n) {
				requesting.more(n);
				int remaining = queue.remainingCapacity();
				if (remaining > 0) {
					delegate.request(remaining);
				}
			}

			@Override
			public void cancel() {
				delegate.cancel();
			}
		}

		private final Subscriber<? super T> delegate;
		private final BlockingQueue<T> queue;
		private Subscription subscription;

		public BufferedSubscriber(Subscriber<? super T> delegate) {
			this.delegate = Require.nonNull(delegate);
			this.queue = new ArrayBlockingQueue<>(capacity, false);
		}

		@Override
		public void onSubscribe(Subscription s) {
			delegate.onSubscribe(new BufferedSubscription(subscription = s));
		}

		@Override
		public void onNext(T value) {
			delegate.onNext(value);
			int current = queue.size();
			if (subscription != null && (current == 0 || capacity / current > refillFactor)) {
				subscription.request(queue.remainingCapacity());
			}
		}

		@Override
		public void onError(Throwable exception) {
			delegate.onError(exception);
		}

		@Override
		public void onComplete() {
			subscription = null;
		}
	}

	private Publisher<T> delegate;
	private int capacity;
	private int refillFactor;

	@Override
	public void subscribe(Subscriber<? super T> s) {
		delegate.subscribe(new BufferedSubscriber(s));
	}
}
