package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Type;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class Multicast<T> implements Processor<T, T> {

	private class MulticastSubscription implements RequestingSubscription {

		private final Subscriber<? super T> subscriber;
		private final Subscriber<? super T> asyncSubscriber;
		private volatile long requested;

		MulticastSubscription(Subscriber<? super T> subscriber, long requested) {
			this.subscriber = Require.nonNull(subscriber);
			this.asyncSubscriber = scheduler.createActorDecorator(Type.of(Subscriber.class), subscriber);
			this.requested = requested;
			asyncSubscriber.onSubscribe(this);
		}

		@Override
		public void cancel() {
			cancelSubscription(subscriber);
		}

		@Override
		public void request(long n) {
			requested = requesting(requested, n);
			requestHandler.handle(subscriber);
		}

		void onNext(T value) {
			asyncSubscriber.onNext(value);
		}

		void onError(Throwable exception) {
			asyncSubscriber.onError(exception);
		}

		void onComplete() {
			asyncSubscriber.onComplete();
		}
	}

	private static final Subscription NONE = new Subscription() {

		@Override
		public void request(long n) {
		}

		@Override
		public void cancel() {
		}
	};

	public static <T> Publisher<T> create(Scheduler scheduler, Publisher<T> delegate) {
		Multicast<T> multicast = new Multicast<>(scheduler);
		delegate.subscribe(multicast);
		return multicast;
	}

	private final Scheduler scheduler;
	private final ConcurrentMap<Subscriber<? super T>, MulticastSubscription> subscriptions;
	private final Actor<Subscriber<? super T>> requestHandler;
	private Subscription subscription;
	private long alreadyRequested;

	public Multicast(Scheduler scheduler) {
		this.scheduler = Require.nonNull(scheduler);
		this.subscriptions = new ConcurrentHashMap<>(1);
		this.requestHandler = scheduler.createActor(this::requestMoreIfNeeded);
		this.subscription = NONE;
		this.alreadyRequested = 0;
	}

	private void cancelSubscription(Subscriber<? super T> subscriber) {
		if (subscriptions.remove(subscriber) != null && subscriptions.isEmpty()) {
			subscription.cancel();
			subscription = NONE;
		}
	}

	private void requestMoreIfNeeded(Subscriber<? super T> source) {
		long request = subscriptions.values().stream().mapToLong(t -> t.requested - alreadyRequested).min().orElse(0);
		if (request > 0) {
			alreadyRequested += request;
			subscription.request(request);
		}
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		Require.that(subscription == NONE);
		this.subscription = Require.nonNull(subscription);
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

	private void performOnSubscriptions(Consumer<MulticastSubscription> action) {
		subscriptions.values().stream().forEach(action);
	}

	@Override
	public void subscribe(Subscriber<? super T> subscriber) {
		subscriptions.computeIfAbsent(subscriber, s -> new MulticastSubscription(s, alreadyRequested));
	}
}
