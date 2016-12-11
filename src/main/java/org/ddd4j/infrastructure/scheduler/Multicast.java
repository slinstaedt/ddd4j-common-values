package org.ddd4j.infrastructure.scheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.ddd4j.contract.Require;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class Multicast<T> extends Actor.Inherited<Multicast<T>> implements Processor<T, T> {

	private static class MulticastSubscription<T> extends Actor.Inherited<MulticastSubscription<T>> implements RequestingSubscription {

		private final Actor<Multicast<T>> processor;
		private final Subscriber<? super T> subscriber;
		private volatile long requested;

		MulticastSubscription(Actor<Multicast<T>> processor, Subscriber<? super T> subscriber, long requested) {
			super(processor.getExecutor(), processor.getBurst());
			this.processor = Require.nonNull(processor);
			this.subscriber = Require.nonNull(subscriber);
			this.requested = requested;
			perform(s -> s.subscriber.onSubscribe(this));
		}

		@Override
		public void cancel() {
			processor.perform(p -> p.cancelSubscription(subscriber));
		}

		void onComplete() {
			perform(s -> s.subscriber.onComplete());
		}

		void onError(Throwable exception) {
			perform(s -> s.subscriber.onError(exception));
		}

		void onNext(T value) {
			perform(s -> s.subscriber.onNext(value));
		}

		@Override
		public void request(long n) {
			requested = requesting(requested, n);
			processor.perform(p -> p.requestMoreIfNeeded());
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

	public static <T> Publisher<T> create(Scheduler scheduler, Publisher<T> delegate) {
		Multicast<T> multicast = new Multicast<>(scheduler, scheduler.getBurstProcessing());
		delegate.subscribe(multicast);
		return multicast;
	}

	private final Map<Subscriber<? super T>, MulticastSubscription<T>> subscriptions;
	private long alreadyRequested;
	private Subscription subscription;

	public Multicast(Executor executor, int burst) {
		super(executor, burst);
		this.subscriptions = new HashMap<>();
		this.alreadyRequested = 0;
		this.subscription = NONE;
	}

	private void cancelSubscription(Subscriber<? super T> subscriber) {
		perform(p -> {
		});
		if (subscriptions.remove(subscriber) != null && subscriptions.isEmpty()) {
			subscription.cancel();
			subscription = NONE;
		}
	}

	private MulticastSubscription<T> newSubscription(Subscriber<? super T> subscriber) {
		return new MulticastSubscription<>(this, subscriber, alreadyRequested);
	}

	@Override
	public void onComplete() {
		performOnSubscriptions(s -> s.onComplete());
	}

	@Override
	public void onError(Throwable exception) {
		performOnSubscriptions(s -> s.onError(exception));
	}

	@Override
	public void onNext(T value) {
		performOnSubscriptions(s -> s.onNext(value));
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		Require.that(subscription == NONE);
		this.subscription = Require.nonNull(subscription);
	}

	private void performOnSubscriptions(Consumer<MulticastSubscription<T>> action) {
		perform(p -> p.subscriptions.values().stream().forEach(action));
	}

	private void requestMoreIfNeeded() {
		long request = subscriptions.values().stream().mapToLong(t -> t.requested - alreadyRequested).min().orElse(0);
		if (request > 0) {
			alreadyRequested += request;
			perform(p -> p.subscription.request(request));
		}
	}

	@Override
	public void subscribe(Subscriber<? super T> subscriber) {
		perform(p -> p.subscriptions.computeIfAbsent(subscriber, this::newSubscription));
	}
}
