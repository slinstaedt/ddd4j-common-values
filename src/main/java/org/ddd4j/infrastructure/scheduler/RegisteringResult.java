package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Result;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public abstract class RegisteringResult<T> implements Result<T> {

	private final ConcurrentMap<Subscriber<? super T>, Subscription> subscribers;

	public RegisteringResult() {
		this.subscribers = new ConcurrentHashMap<>(2);
	}

	@Override
	public void subscribe(Subscriber<? super T> subscriber) {
		Require.nonNull(subscriber);
		subscribers.computeIfAbsent(subscriber, this::subscribeNew);
	}

	protected abstract Subscription subscribeNew(Subscriber<? super T> subscriber);

	protected boolean unsubscribe(Subscriber<? super T> subscriber) {
		return subscribers.remove(subscriber) != null;
	}
}
