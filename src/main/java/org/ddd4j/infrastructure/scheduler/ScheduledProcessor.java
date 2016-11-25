package org.ddd4j.infrastructure.scheduler;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import org.ddd4j.contract.Require;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class ScheduledProcessor<T> implements Processor<T, T> {

	private class Target implements Subscription, Runnable {

		private final Subscriber<? super T> subscriber;
		private final Queue<T> queue;
		private long requested;

		public Target(Subscriber<? super T> subscriber, long requested) {
			this.subscriber = Require.nonNull(subscriber);
			this.queue = new ConcurrentLinkedQueue<>();
			this.requested = requested;
		}

		@Override
		public void cancel() {
			cancelSubscription(subscriber);
		}

		void onComplete() {
			cancel();
			subscriber.onComplete();
		}

		void onError(Throwable exception) {
			cancel();
			subscriber.onError(exception);
		}

		void onNext(T value) {
			queue.offer(value);
		}

		@Override
		public void request(long n) {
			requested += n;
			if (requested < 0) {
				requested = Long.MAX_VALUE;
			}
			requestMoreIfNeeded();
		}

		@Override
		public void run() {
			T value = null;
			while ((value = queue.poll()) != null) {
				requested--;
				subscriber.onNext(value);
			}
		}
	}

	private static final Subscription NONE = new Subscription() {

		@Override
		public void cancel() {
		}

		@Override
		public void request(long n) {
		}
	};

	private final Executor executor;
	private final Agent<ScheduledProcessor<T>> self;
	private final ConcurrentMap<Subscriber<? super T>, Agent<Target>> subscriptions;
	private final AtomicLong alreadyRequested;
	private Subscription subscription;

	public ScheduledProcessor(Executor executor) {
		this.executor = Require.nonNull(executor);
		this.self = new Agent<>(executor, this);
		this.subscriptions = new ConcurrentHashMap<>();
		this.alreadyRequested = new AtomicLong(0);
		this.subscription = NONE;
	}

	private void cancelSubscription(Subscriber<? super T> subscriber) {
		if (subscriptions.remove(subscriber) != null && subscriptions.isEmpty()) {
			executor.execute(subscription::cancel);
			subscription = NONE;
		}
	}

	private Agent<Target> newSubscription(Subscriber<? super T> subscriber) {
		Target target = new Target(subscriber, alreadyRequested.get());
		subscriber.onSubscribe(target);
		return new Agent<>(executor, target);
	}

	private void requestMoreIfNeeded() {
		long already = alreadyRequested.get();
		long request = subscriptions.values().stream().mapToLong(t -> t.getDelegate().requested).map(r -> r - already).min().orElse(0);
		if (request > 0 && alreadyRequested.compareAndSet(already, already + request)) {
			executor.execute(() -> subscription.request(request));
		}
	}

	@Override
	public void onComplete() {
		subscriptions.values().stream().forEach(a -> a.perform(t -> t.onComplete()));
	}

	@Override
	public void onError(Throwable exception) {
		subscriptions.values().stream().forEach(a -> a.perform(t -> t.onError(exception)));
	}

	@Override
	public void onNext(T value) {
		alreadyRequested.decrementAndGet();
		subscriptions.values().stream().forEach(a -> a.perform(t -> t.onNext(value)));
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		Require.that(subscription == NONE);
		this.subscription = Require.nonNull(subscription);
	}

	@Override
	public void subscribe(Subscriber<? super T> subscriber) {
		subscriptions.computeIfAbsent(subscriber, this::newSubscription);
	}
}
