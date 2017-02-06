package org.ddd4j.eventstore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import org.ddd4j.contract.Require;
import org.ddd4j.eventstore.Repository.Publisher;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.RevisionsCallback;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revisions;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

// TODO delegate to subscriber in a async way?
public class ChannelPublisher implements Publisher<ReadBuffer, ReadBuffer> {

	private class ChannelSubscription implements Subscription {

		private final Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber;
		private final RevisionsCallback callback;
		private Revisions expected;

		ChannelSubscription(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber, RevisionsCallback callback, int partitionSize) {
			this.subscriber = Require.nonNull(subscriber);
			this.callback = Require.nonNull(callback);
			expected = new Revisions(partitionSize);
			subscriber.onSubscribe(this);
		}

		@Override
		public void cancel() {
			unsubscribe(subscriber);
		}

		void loadRevisions(IntStream partitions) {
			expected = expected.update(callback.loadRevisions(partitions));
		}

		void onError(Throwable throwable) {
			subscriber.onError(throwable);
			unsubscribe(subscriber);
		}

		void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
			if (expected.reachedBy(committed.getActual())) {
				// skip old commits
				return;
			}
			subscriber.onNext(committed);
			expected = expected.update(committed.getExpected());
			// TODO
		}

		@Override
		public void request(long n) {
			// TODO Auto-generated method stub

		}

		void saveRevisions(IntStream partitions) {
			callback.saveRevisions(expected);
		}
	}

	private final ResourceDescriptor topic;
	private final Map<Subscriber<Committed<ReadBuffer, ReadBuffer>>, ChannelSubscription> subscriptions;
	private int partitionSize;

	public ChannelPublisher(ResourceDescriptor topic) {
		this.topic = Require.nonNull(topic);
		this.subscriptions = new ConcurrentHashMap<>();
	}

	void loadRevisions(IntStream partitions) {
		subscriptions.values().forEach(s -> s.loadRevisions(partitions));
	}

	void onError(Throwable throwable) {
		subscriptions.values().forEach(c -> c.onError(throwable));
	}

	void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
		subscriptions.values().forEach(c -> c.onNext(committed));
	}

	void saveRevisions(IntStream partitions) {
		subscriptions.values().forEach(s -> s.saveRevisions(partitions));
	}

	@Override
	public void subscribe(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber, RevisionsCallback callback) {
		subscriptions.computeIfAbsent(subscriber, s -> new ChannelSubscription(s, callback, partitionSize));
	}

	boolean unsubscribe(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber) {
		subscriptions.remove(subscriber);
		return subscriptions.isEmpty();
	}
}
