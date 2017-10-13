package org.ddd4j.repository;

import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Consumer;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.value.versioned.Committed;

public class ReactiveListener<K, V> implements CommitListener<K, V>, ErrorListener, Subscription {

	private final Subscriber<? super Committed<K, V>> subscriber;
	private final Consumer<? super CommitListener<K, V>> unsubscriber;
	private final Requesting requesting;

	public ReactiveListener(Subscriber<? super Committed<K, V>> subscriber, Consumer<? super CommitListener<K, V>> unsubscriber) {
		this.subscriber = Require.nonNull(subscriber);
		this.unsubscriber = Require.nonNull(unsubscriber);
		this.requesting = new Requesting();
		subscriber.onSubscribe(this);
	}

	@Override
	public void cancel() {
		unsubscriber.accept(this);
	}

	@Override
	public void onError(Throwable throwable) {
		subscriber.onError(throwable);
	}

	@Override
	public void onNext(ChannelName name, Committed<K, V> committed) {
		subscriber.onNext(committed);
	}

	@Override
	public void request(long n) {
		requesting.more(n);
	}
}