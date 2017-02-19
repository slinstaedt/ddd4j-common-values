package org.ddd4j.log;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ColdChannel;
import org.ddd4j.infrastructure.channel.HotChannel;
import org.ddd4j.infrastructure.channel.RevisionsCallback;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.log.Log.Publisher;
import org.ddd4j.value.Lazy;
import org.ddd4j.value.math.Ordered.Comparison;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

// TODO delegate to subscriber in a async way?
public class ChannelPublisher implements Publisher<ReadBuffer, ReadBuffer> {

	private class ChannelSubscription implements Subscription {

		private final Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber;
		private final RevisionsCallback callback;
		private final Revisions expected;
		private final Requesting requesting;

		ChannelSubscription(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber, RevisionsCallback callback, int partitionSize) {
			this.subscriber = Require.nonNull(subscriber);
			this.callback = Require.nonNull(callback);
			this.expected = new Revisions(partitionSize);
			this.requesting = new Requesting();
			subscriber.onSubscribe(this);
		}

		@Override
		public void cancel() {
			unsubscribe(subscriber);
		}

		void loadRevisions(int[] partitions) {
			expected.update(callback.loadRevisions(Arrays.stream(partitions)));
		}

		void onError(Throwable throwable) {
			subscriber.onError(throwable);
			unsubscribe(subscriber);
		}

		void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
			if (!requesting.hasRemaining()) {
				return;
			}

			Revision actualRevision = committed.getActual();
			Revision expectedRevision = expected.revisionOfPartition(actualRevision.getPartition());
			Comparison expectation = actualRevision.compare(expectedRevision);
			if (expectation == Comparison.EQUAL) {
				subscriber.onNext(committed);
				expected.update(committed.getNextExpected());
				requesting.processed();
			} else if (expectation == Comparison.LARGER) {
				coldCallback.seek(topic, expectedRevision);
				// TODO
			}
		}

		@Override
		public void request(long n) {
			requesting.more(n);
		}

		void saveRevisions(int[] partitions) {
			callback.saveRevisions(expected.revisionsOfPartitions(Arrays.stream(partitions)));
		}
	}

	private final ResourceDescriptor topic;
	private final ColdChannel.Callback coldCallback;
	private final HotChannel.Callback hotCallback;
	private final Lazy<Revisions> current;
	private final Map<Subscriber<Committed<ReadBuffer, ReadBuffer>>, ChannelSubscription> subscriptions;

	public ChannelPublisher(ResourceDescriptor topic, ColdChannel.Callback coldCallback, HotChannel.Callback hotCallback) {
		this.topic = Require.nonNull(topic);
		this.coldCallback = Require.nonNull(coldCallback);
		this.hotCallback = Require.nonNull(hotCallback);
		this.current = new Lazy<>(() -> new Revisions(hotCallback.subscribe(topic)), r -> hotCallback.unsubscribe(topic));
		this.subscriptions = new ConcurrentHashMap<>();
	}

	private void forAllSubscriptions(Consumer<ChannelSubscription> action) {
		subscriptions.values().forEach(action);
	}

	void loadRevisions(int[] partitions) {
		current.ifPresent(r -> r.updateWithPartitions(Arrays.stream(partitions), 0));
		forAllSubscriptions(s -> s.loadRevisions(partitions));
	}

	void onError(Throwable throwable) {
		forAllSubscriptions(c -> c.onError(throwable));
	}

	void onNextCold(Committed<ReadBuffer, ReadBuffer> committed) {
		// if (current.get().handleSuccess(r->r.compare(committed.getNextExpected())))
		// TODO unseek cold when committed reached current
		forAllSubscriptions(c -> c.onNext(committed));
	}

	void onNextHot(Committed<ReadBuffer, ReadBuffer> committed) {
		// if (current.get().handleSuccess(r->r.compare(committed.getNextExpected())))
		// TODO unseek cold when committed reached current
		current.ifPresent(r -> r.update(committed.getActual()));
		forAllSubscriptions(c -> c.onNext(committed));
	}

	void saveRevisions(int[] partitions) {
		forAllSubscriptions(s -> s.saveRevisions(partitions));
		current.ifPresent(r -> r.reset(Arrays.stream(partitions)));
	}

	@Override
	public void subscribe(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber, RevisionsCallback callback) {
		try {
			int partitionSize = current.get().getPartitionSize();
			subscriptions.computeIfAbsent(subscriber, s -> new ChannelSubscription(s, callback, partitionSize));
		} catch (Exception e) {
			subscriber.onError(e);
			current.destroy();
		}
	}

	private void unsubscribe(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber) {
		subscriptions.remove(subscriber);
		if (subscriptions.isEmpty()) {
			current.destroy();
		}
	}
}
