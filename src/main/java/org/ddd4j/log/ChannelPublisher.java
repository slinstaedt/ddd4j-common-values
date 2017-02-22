package org.ddd4j.log;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
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

		void earliestRevision(Revisions revisions) {
			revisions.updateIfEarlier(expected);
		}

		Promise<Void> loadRevisions(int[] partitions) {
			return callback.loadRevisions(Arrays.stream(partitions))
					.sync()
					.whenCompleteSuccessfully(expected::update)
					.thenReturnValue(null);
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
				expected.update(committed.getNextExpected());
				requesting.processed();
			} else if (actual == Comparison.LARGER) {
				seek(expectedRevision);
				// TODO
			}
		}

		@Override
		public void request(long n) {
			requesting.more(n);
			seekIfNecessary(expected);
		}

		Promise<Void> saveRevisions(int[] partitions) {
			return callback.saveRevisions(expected.revisionsOfPartitions(Arrays.stream(partitions)))
					.sync()
					.thenRun(() -> expected.reset(Arrays.stream(partitions)));
		}
	}

	private final ResourceDescriptor topic;
	private final ColdChannel.Callback coldCallback;
	private final Lazy<Revisions> hotRevisions;
	private final Map<Subscriber<Committed<ReadBuffer, ReadBuffer>>, ChannelSubscription> subscriptions;

	public ChannelPublisher(ResourceDescriptor topic, ColdChannel.Callback coldCallback, HotChannel.Callback hotCallback) {
		this.topic = Require.nonNull(topic);
		this.coldCallback = Require.nonNull(coldCallback);
		this.hotRevisions = new Lazy<>(() -> new Revisions(hotCallback.subscribe(topic).join()), r -> hotCallback.unsubscribe(topic));
		this.subscriptions = new ConcurrentHashMap<>();
	}

	private void forAllSubscriptions(Consumer<ChannelSubscription> action) {
		subscriptions.values().forEach(action);
	}

	void loadRevisions(int[] partitions) {
		subscriptions.values()
				.stream()
				.map((s -> s.loadRevisions(partitions)))
				.reduce(Promise.COMPLETED, Promise::runAfterBoth)
				.thenReturnValue(partitions)
				.whenCompleteSuccessfully(this::seekIfNecessary);
	}

	private void seek(Revision revision) {
		coldCallback.seek(topic, revision);
	}

	private void seekIfNecessary(int[] partitions) {
		hotRevisions.ifPresent(r -> {
			Revisions earliest = Revisions.create(r.getPartitionSize(), Arrays.stream(partitions), Long.MAX_VALUE);
			forAllSubscriptions(s -> s.earliestRevision(earliest));
			r.update(earliest.revisions());
			earliest.revisions().forEach(this::seek);
		});
	}

	private void seekIfNecessary(Revisions expected) {
		hotRevisions.ifPresent(r -> expected.diffOffsetsFrom(r).forEach(this::seek));
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
		subscriptions.values().stream().map(s -> s.saveRevisions(partitions)).reduce(Promise.COMPLETED, Promise::runAfterBoth).join();
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
