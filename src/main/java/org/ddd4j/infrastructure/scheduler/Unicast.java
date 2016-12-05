package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Type;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class Unicast<T> implements Publisher<T> {

	private class UnicastSubscription implements RequestingSubscription, Subscriber<T> {

		private final Subscriber<? super T> subscriber;
		private final Subscriber<? super T> asyncSubscriber;
		private Subscription asyncSubscription;

		UnicastSubscription(Subscriber<? super T> subscriber) {
			this.subscriber = Require.nonNull(subscriber);
			this.asyncSubscriber = scheduler.createActorDecorator(Type.of(Subscriber.class), subscriber);
		}

		@Override
		public void cancel() {
			if (subscriptions.remove(subscriber) != null) {
				asyncSubscription.cancel();
			}
		}

		@Override
		public void request(long n) {
			if (subscriptions.containsKey(subscriber)) {
				asyncSubscription.request(n);
			}
		}

		@Override
		public void onSubscribe(Subscription subscription) {
			asyncSubscription = scheduler.createActorDecorator(Type.of(Subscription.class), subscription);
			asyncSubscriber.onSubscribe(this);
		}

		@Override
		public void onNext(T value) {
			asyncSubscriber.onNext(value);
		}

		@Override
		public void onError(Throwable exception) {
			asyncSubscriber.onError(exception);
			subscriptions.remove(subscriber);
		}

		@Override
		public void onComplete() {
			asyncSubscriber.onComplete();
			subscriptions.remove(subscriber);
		}
	}

	public static <T> Publisher<T> create(Scheduler scheduler, Publisher<T> delegate) {
		return new Unicast<>(scheduler, delegate);
	}

	private final Scheduler scheduler;
	private final Publisher<T> delegate;
	private final ConcurrentMap<Subscriber<? super T>, UnicastSubscription> subscriptions;

	public Unicast(Scheduler scheduler, Publisher<T> delegate) {
		this.scheduler = Require.nonNull(scheduler);
		this.delegate = Require.nonNull(delegate);
		this.subscriptions = new ConcurrentHashMap<>(1);
	}

	private UnicastSubscription newSubscription(Subscriber<? super T> subscriber) {
		UnicastSubscription subscription = new UnicastSubscription(subscriber);
		delegate.subscribe(subscription);
		return subscription;
	}

	@Override
	public void subscribe(Subscriber<? super T> subscriber) {
		subscriptions.computeIfAbsent(subscriber, this::newSubscription);
	}
}
