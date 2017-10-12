package org.ddd4j.repository;

import java.util.function.Consumer;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.channel.api.CompletionListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.SourceListener;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.value.versioned.Committed;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class ReactiveSubscriber<K, V> implements Subscriber<Committed<K, V>> {

	private final ChannelName name;
	private final SourceListener<K, V> source;
	private final CompletionListener completion;
	private final ErrorListener error;
	private final Consumer<? super Subscription> onSubscribed;

	public ReactiveSubscriber(ChannelName name, SourceListener<K, V> source, CompletionListener completion, ErrorListener error,
			Consumer<? super Subscription> onSubscribed) {
		this.name = Require.nonNull(name);
		this.source = Require.nonNull(source);
		this.completion = Require.nonNull(completion);
		this.error = Require.nonNull(error);
		this.onSubscribed = Require.nonNull(onSubscribed);
	}

	@Override
	public void onComplete() {
		completion.onComplete();
	}

	@Override
	public void onError(Throwable throwable) {
		error.onError(throwable);
	}

	@Override
	public void onNext(Committed<K, V> committed) {
		source.onNext(name, committed);
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		onSubscribed.accept(subscription);
	}
}
