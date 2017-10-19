package org.ddd4j.repository;

import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.CompletionListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.value.versioned.Committed;

public class FlowListener<K, V> implements CommitListener<K, V>, ErrorListener, CompletionListener, Subscription {

	private final Subscriber<? super Committed<K, V>> subscriber;
	private final Consumer<? super Subscriber<?>> unsubscriber;
	private final IntConsumer requestor;
	private final Requesting requesting;

	public FlowListener(Subscriber<? super Committed<K, V>> subscriber, Consumer<? super Subscriber<?>> unsubscriber,
			IntConsumer requestor) {
		this.subscriber = Require.nonNull(subscriber);
		this.unsubscriber = Require.nonNull(unsubscriber);
		this.requestor = Require.nonNull(requestor);
		this.requesting = new Requesting();
		subscriber.onSubscribe(this);
	}

	@Override
	public void cancel() {
		unsubscriber.accept(subscriber);
	}

	@Override
	public void onComplete() {
		subscriber.onComplete();
	}

	@Override
	public void onError(Throwable throwable) {
		subscriber.onError(throwable);
	}

	@Override
	public void onNext(ChannelName name, Committed<K, V> committed) {
		if (requesting.hasRemaining()) {
			subscriber.onNext(committed);
			requesting.processed();
		}
	}

	@Override
	public void request(long n) {
		requesting.more(n, requestor);
	}
}