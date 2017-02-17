package org.ddd4j.log;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ColdChannel;
import org.ddd4j.infrastructure.channel.HotChannel;
import org.ddd4j.infrastructure.channel.RevisionsCallback;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.log.Log.Publisher;
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

		void saveRevisions(int[] partitions) {
			callback.saveRevisions(expected.revisionsOfPartitions(Arrays.stream(partitions)));
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
	}

	private final ResourceDescriptor topic;
	private final ColdChannel.Callback coldCallback;
	private final HotChannel.Callback hotCallback;
	private final AtomicReference<Promise<Revisions>> current;
	private final Map<Subscriber<Committed<ReadBuffer, ReadBuffer>>, ChannelSubscription> subscriptions;

	public ChannelPublisher(ResourceDescriptor topic, ColdChannel.Callback coldCallback, HotChannel.Callback hotCallback) {
		this.topic = Require.nonNull(topic);
		this.coldCallback = Require.nonNull(coldCallback);
		this.hotCallback = Require.nonNull(hotCallback);
		this.current = new AtomicReference<>();
		this.subscriptions = new ConcurrentHashMap<>();
	}

	private void forAllSubscriptions(Consumer<ChannelSubscription> action) {
		subscriptions.values().forEach(action);
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
		forAllSubscriptions(c -> c.onNext(committed));
	}

	void loadRevisions(int[] partitions) {
		current.get().whenCompleteSuccessfully(r -> r.updateWithPartitions(partitions, 0));
		forAllSubscriptions(s -> s.loadRevisions(partitions));
	}

	void saveRevisions(int[] partitions) {
		forAllSubscriptions(s -> s.saveRevisions(partitions));
		current.get().whenCompleteSuccessfully(r -> r.reset(partitions));
	}

	@Override
	public void subscribe(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber, RevisionsCallback callback) {
		current.updateAndGet(p -> p != null ? p : hotCallback.subscribe(topic).sync().handleSuccess(Revisions::new))
				.whenCompleteExceptionally(e -> subscriber.onError(e))
				.whenCompleteExceptionally(e -> current.set(null))
				.handleSuccess(Revisions::getPartitionSize)
				.whenCompleteSuccessfully(
						size -> subscriptions.computeIfAbsent(subscriber, s -> new ChannelSubscription(s, callback, size)));
	}

	private void unsubscribe(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber) {
		subscriptions.remove(subscriber);
		if (subscriptions.isEmpty()) {
			current.updateAndGet(promise -> {
				if (promise != null) {
					hotCallback.unsubscribe(topic);
				}
				return null;
			});
		}
	}
}
