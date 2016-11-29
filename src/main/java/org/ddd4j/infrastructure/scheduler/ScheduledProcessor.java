package org.ddd4j.infrastructure.scheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.ddd4j.contract.Require;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class ScheduledProcessor<T> extends Agent.Inherited<ScheduledProcessor<T>> implements Processor<T, T> {

	private static class ScheduledSubscription<T> extends Agent.Inherited<ScheduledSubscription<T>> implements RequestingSubscription {

		private final Agent<ScheduledProcessor<T>> processor;
		private final Subscriber<? super T> subscriber;
		private volatile long requested;

		ScheduledSubscription(Agent<ScheduledProcessor<T>> processor, Subscriber<? super T> subscriber, long requested) {
			super(processor.getExecutor());
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

	private final Map<Subscriber<? super T>, ScheduledSubscription<T>> subscriptions;
	private long alreadyRequested;
	private Subscription subscription;

	public ScheduledProcessor(Executor executor) {
		super(executor);
		this.subscriptions = new HashMap<>();
		this.alreadyRequested = 0;
		this.subscription = NONE;
	}

	private void cancelSubscription(Subscriber<? super T> subscriber) {
		if (subscriptions.remove(subscriber) != null && subscriptions.isEmpty()) {
			subscription.cancel();
			subscription = NONE;
		}
	}

	private ScheduledSubscription<T> newSubscription(Subscriber<? super T> subscriber) {
		return new ScheduledSubscription<>(this, subscriber, alreadyRequested);
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

	private void performOnSubscriptions(Consumer<ScheduledSubscription<T>> action) {
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
