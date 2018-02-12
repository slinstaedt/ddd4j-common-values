package org.ddd4j.infrastructure.publisher;

import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.SchemaCodec;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.CompletionListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.spi.FlowControlled;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.publisher.ChannelPublisher.ListenerFactory;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;

public class FlowSubscription<K, V> implements Subscription, CommitListener<K, V>, ErrorListener, CompletionListener {

	public static <K, V, C> ChannelListener createListener(ListenerFactory<C> factory, C callback,
			Subscriber<? super Committed<K, V>> subscriber, Consumer<? super Subscriber<?>> unsubscriber,
			SchemaCodec.DecodingFactory<K, V> decodingFactory) {
		ChannelListener[] listener = new ChannelListener[1];
		FlowSubscription<K, V> subscription = new FlowSubscription<>(subscriber, unsubscriber,
				l -> listener[0] = factory.create(decodingFactory.create(l, l), l, callback));
		subscriber.onSubscribe(subscription);
		return listener[0];
	}

	public static <C> ChannelListener createListener(ListenerFactory<C> factory, C callback,
			Subscriber<? super Committed<ReadBuffer, ReadBuffer>> subscriber, Consumer<? super Subscriber<?>> unsubscriber) {
		return createListener(factory, callback, subscriber, unsubscriber, (c, e) -> c);
	}

	private final Subscriber<? super Committed<K, V>> subscriber;
	private final Consumer<? super Subscriber<?>> unsubscriber;
	private final Requesting requesting;
	private final FlowControlled<?> flow;

	private FlowSubscription(Subscriber<? super Committed<K, V>> subscriber, Consumer<? super Subscriber<?>> unsubscriber,
			Function<FlowSubscription<K, V>, FlowControlled<?>> flowFactory) {
		this.subscriber = Require.nonNull(subscriber);
		this.unsubscriber = Require.nonNull(unsubscriber);
		this.requesting = new Requesting();
		this.flow = flowFactory.apply(this);
	}

	@Override
	public void cancel() {
		unsubscriber.accept(subscriber);
	}

	@Override
	public Promise<?> onComplete() {
		return Promise.completed().whenComplete(subscriber::onComplete);
	}

	@Override
	public Promise<?> onError(Throwable throwable) {
		return Promise.completed(throwable).whenCompleteSuccessfully(subscriber::onError);
	}

	@Override
	public Promise<?> onNext(ChannelName name, Committed<K, V> committed) {
		return Promise.completed(committed).whenCompleteSuccessfully(this::onNext);
	}

	private void onNext(Committed<K, V> committed) {
		if (requesting.hasRemaining()) {
			subscriber.onNext(committed);
			if (requesting.processed()) {
				flow.pause();
			}
		}
	}

	@Override
	public void request(long n) {
		if (n > 0) {
			if (requesting.more(n)) {
				flow.resume();
			}
		} else {
			subscriber.onError(new IllegalArgumentException("Requested: " + n));
		}
	}
}