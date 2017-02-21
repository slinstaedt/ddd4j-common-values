package org.ddd4j.log;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ColdChannel;
import org.ddd4j.infrastructure.channel.HotChannel;
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

		private void checkRevisionStateAgainst(Revisions against) {
			expected.diffOffsetsFrom(against).forEach(r -> coldCallback.seek(topic, r));
		}

		void earliestRevision(Revisions revisions) {
			revisions.updateIfEarlier(expected);
		}

		void loadRevisions(int[] partitions) {
			expected.update(callback.loadRevisions(Arrays.stream(partitions)));
		}

		void onError(Throwable throwable) {
			subscriber.onError(throwable);
		}

		void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
			if (!requesting.hasRemaining()) {
				return;
			}

			Revision actualRevision = committed.getActual();
			Revision expectedRevision = expected.revisionOfPartition(actualRevision.getPartition());
			Comparison actual = actualRevision.compare(expectedRevision);
			if (actual == Comparison.EQUAL) {
				subscriber.onNext(committed);
				// TODO use actual or next?
				expected.update(committed.getNextExpected());
				requesting.processed();
			} else if (actual == Comparison.LARGER) {
				coldCallback.seek(topic, expectedRevision);
				// TODO
			}
		}

		@Override
		public void request(long n) {
			requesting.more(n);
			hotRevisions.ifPresent(this::checkRevisionStateAgainst);
		}

		void saveRevisions(int[] partitions) {
			callback.saveRevisions(expected.revisionsOfPartitions(Arrays.stream(partitions)));
			expected.reset(Arrays.stream(partitions));
		}
	}

	private final ResourceDescriptor topic;
	private final ColdChannel.Callback coldCallback;
	private final HotChannel.Callback hotCallback;
	private final Lazy<Revisions> hotRevisions;
	private final Map<Subscriber<Committed<ReadBuffer, ReadBuffer>>, ChannelSubscription> subscriptions;

	public ChannelPublisher(ResourceDescriptor topic, ColdChannel.Callback coldCallback, HotChannel.Callback hotCallback) {
		this.topic = Require.nonNull(topic);
		this.coldCallback = Require.nonNull(coldCallback);
		this.hotCallback = Require.nonNull(hotCallback);
		this.hotRevisions = new Lazy<>(() -> new Revisions(hotCallback.subscribe(topic).join()), r -> hotCallback.unsubscribe(topic));
		this.subscriptions = new ConcurrentHashMap<>();
	}

	private void forAllSubscriptions(Consumer<ChannelSubscription> action) {
		subscriptions.values().forEach(action);
	}

	void loadRevisions(int[] partitions) {
		forAllSubscriptions(s -> s.loadRevisions(partitions));
		hotRevisions.ifPresent(r -> {
			Revisions earliest = Revisions.create(r.getPartitionSize(), Arrays.stream(partitions), Long.MAX_VALUE);
			forAllSubscriptions(s -> s.earliestRevision(earliest));
			r.update(earliest.revisions());
			earliest.revisions().forEach(rev -> coldCallback.seek(topic, rev));
		});
	}

	void onError(Throwable throwable) {
		forAllSubscriptions(s -> s.onError(throwable));
		subscriptions.clear();
		coldCallback.close();
		hotRevisions.close();
	}

	void onNextCold(Committed<ReadBuffer, ReadBuffer> committed) {
		hotRevisions.ifPresent(r -> {
			Revision nextExpected = committed.getNextExpected();
			if (r.compare(nextExpected) == Comparison.EQUAL) {
				coldCallback.unseek(topic, nextExpected.getPartition());
			}
		});
		forAllSubscriptions(s -> s.onNext(committed));
	}

	void onNextHot(Committed<ReadBuffer, ReadBuffer> committed) {
		hotRevisions.ifPresent(r -> r.update(committed.getNextExpected()));
		forAllSubscriptions(s -> s.onNext(committed));
	}

	void saveRevisions(int[] partitions) {
		forAllSubscriptions(s -> s.saveRevisions(partitions));
		hotRevisions.ifPresent(r -> r.reset(Arrays.stream(partitions)));
	}

	@Override
	public void subscribe(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber, RevisionsCallback callback) {
		try {
			int partitionSize = hotRevisions.get().getPartitionSize();
			subscriptions.computeIfAbsent(subscriber, s -> new ChannelSubscription(s, callback, partitionSize));
		} catch (Exception e) {
			subscriber.onError(e);
			hotRevisions.destroy(Revisions::hashCode);
		}
	}

	private void unsubscribe(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber) {
		subscriptions.remove(subscriber);
		if (subscriptions.isEmpty()) {
			hotRevisions.destroy();
		}
	}
}
