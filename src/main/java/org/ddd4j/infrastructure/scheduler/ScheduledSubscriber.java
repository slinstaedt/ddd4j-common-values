package org.ddd4j.infrastructure.scheduler;

import org.ddd4j.contract.Require;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class ScheduledSubscriber<T> implements Subscriber<T> {

	private final Agent<Subscriber<T>> delegate;

	public ScheduledSubscriber(Agent<Subscriber<T>> delegate) {
		this.delegate = Require.nonNull(delegate);
	}

	@Override
	public void onComplete() {
		delegate.perform(s -> s.onComplete());
	}

	@Override
	public void onError(Throwable exception) {
		delegate.perform(s -> s.onError(exception));
	}

	@Override
	public void onNext(T value) {
		delegate.perform(s -> s.onNext(value));
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		delegate.perform(s -> s.onSubscribe(subscription));
	}
}