package org.ddd4j.log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ChannelListener.ColdChannelCallback;
import org.ddd4j.infrastructure.channel.ChannelListener.HotChannelCallback;
import org.ddd4j.infrastructure.channel.RevisionsCallback;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.log.Log.Publisher;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revisions;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

// TODO delegate to subscriber in a async way?
public class ChannelPublisher implements Publisher<ReadBuffer, ReadBuffer> {

	private class ChannelSubscription implements Subscription {

		private final Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber;
		private final RevisionsCallback callback;
		private Revisions position;

		ChannelSubscription(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber, RevisionsCallback callback, int partitionSize) {
			this.subscriber = Require.nonNull(subscriber);
			this.callback = Require.nonNull(callback);
			position = new Revisions(partitionSize);
			subscriber.onSubscribe(this);
		}

		@Override
		public void cancel() {
			unsubscribe(subscriber);
		}

		void loadRevisions(IntStream partitions) {
			position = position.with(callback.loadRevisions(partitions));
		}

		void onError(Throwable throwable) {
			subscriber.onError(throwable);
			unsubscribe(subscriber);
		}

		void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
			if (position.reachedBy(committed.getActual())) {
				// skip old commits
				return;
			}
			subscriber.onNext(committed);
			position = position.with(committed.getNextExpected());
			// TODO
		}

		@Override
		public void request(long n) {
			// TODO Auto-generated method stub
		}

		void saveRevisions(IntStream partitions) {
			callback.saveRevisions(position);
		}
	}

	private final ResourceDescriptor topic;
	private final ColdChannelCallback coldCallback;
	private final HotChannelCallback hotCallback;
	private final Map<Subscriber<Committed<ReadBuffer, ReadBuffer>>, Promise<ChannelSubscription>> subscriptions;

	public ChannelPublisher(ResourceDescriptor topic, ColdChannelCallback coldCallback, HotChannelCallback hotCallback) {
		this.topic = Require.nonNull(topic);
		this.coldCallback = Require.nonNull(coldCallback);
		this.hotCallback = Require.nonNull(hotCallback);
		this.subscriptions = new ConcurrentHashMap<>();
	}

	private void forAllSubscriptions(Consumer<ChannelSubscription> action) {
		subscriptions.values().forEach(p -> p.whenCompleteSuccessfully(action));
	}

	void loadRevisions(IntStream partitions) {
		forAllSubscriptions(s -> s.loadRevisions(partitions));
	}

	void onError(Throwable throwable) {
		forAllSubscriptions(c -> c.onError(throwable));
	}

	void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
		forAllSubscriptions(c -> c.onNext(committed));
	}

	void saveRevisions(IntStream partitions) {
		forAllSubscriptions(s -> s.saveRevisions(partitions));
	}

	@Override
	public void subscribe(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber, RevisionsCallback callback) {
		if (subscriptions.isEmpty()) {
			hotCallback.subscribe(topic);
		}
		subscriptions.computeIfAbsent(subscriber,
				s -> hotCallback.subscribe(topic)
						.whenCompleteExceptionally(e -> subscriber.onError(e))
						.whenCompleteExceptionally(e -> subscriptions.remove(subscriber))
						.handleSuccess(size -> new ChannelSubscription(s, callback, size)));
	}

	private boolean unsubscribe(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber) {
		subscriptions.remove(subscriber);
		if (subscriptions.isEmpty()) {
			hotCallback.unsubscribe(topic);
		}
		return subscriptions.isEmpty();
	}
}
