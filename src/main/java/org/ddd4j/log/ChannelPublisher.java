package org.ddd4j.log;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.old.Channel;
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

	public static final ChannelPublisher VOID = new ChannelPublisher();

	private final ResourceDescriptor topic;
	private final Channel.Callback callback;
	private final Lazy<Revisions> latest;
	private final Map<Subscriber<Committed<ReadBuffer, ReadBuffer>>, ChannelSubscription> subscriptions;

	private ChannelPublisher() {
		this.topic = null;
		this.callback = null;
		this.latest = new Lazy<>(creator);
		this.subscriptions = Collections.emptyMap();
	}

	public ChannelPublisher(ResourceDescriptor topic, Channel.Callback callback) {
		this.topic = Require.nonNull(topic);
		this.callback = Require.nonNull(callback);
		this.latest = new Lazy<>(() -> new Revisions(callback.subscribe(topic).join()), r -> callback.unsubscribe(topic));
		this.subscriptions = new ConcurrentHashMap<>();
	}

	private void forAllSubscriptions(Consumer<ChannelSubscription> action) {
		subscriptions.values().forEach(action);
	}

	void loadRevisions(int[] partitions) {
		subscriptions.values()
				.stream()
				.map((s -> s.loadRevisions(partitions)))
				.reduce(Promise.completed(), Promise::runAfterBoth)
				.thenReturnValue(partitions)
				.whenCompleteSuccessfully(this::seekIfNecessary);
	}

	void onError(Throwable throwable) {
		forAllSubscriptions(s -> s.onError(throwable));
		subscriptions.clear();
		callback.close();
	}

	void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
		latest.ifPresent(r -> r.updateIfLater(committed.getNextExpected()));
		forAllSubscriptions(s -> s.onNext(committed));
	}

	void saveRevisions(int[] partitions) {
		subscriptions.values().stream().map(s -> s.saveRevisions(partitions)).reduce(Promise.completed(), Promise::runAfterBoth).join();
	}

	private void seek(Revision revision) {
		callback.seek(topic, revision);
	}

	private void seekIfNecessary(int[] partitions) {
		latest.ifPresent(r -> {
			Revisions earliest = Revisions.create(r.getPartitionSize(), Arrays.stream(partitions), Long.MAX_VALUE);
			forAllSubscriptions(s -> s.earliestRevision(earliest));
			r.update(earliest.stream());
			earliest.stream().forEach(this::seek);
		});
	}

	private void seekIfNecessary(Revisions expected) {
		latest.ifPresent(r -> expected.diffOffsetsFrom(r).forEach(this::seek));
	}

	@Override
	public void subscribe(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber, RevisionsCallback callback) {
		try {
			int partitionSize = latest.get().getPartitionSize();
			subscriptions.computeIfAbsent(subscriber, s -> new ChannelSubscription(s, callback, partitionSize));
		} catch (Exception e) {
			subscriber.onError(e);
			latest.destroy(Revisions::getClass);
		}
	}

	private void unsubscribe(Subscriber<Committed<ReadBuffer, ReadBuffer>> subscriber) {
		subscriptions.remove(subscriber);
		if (subscriptions.isEmpty()) {
			callback.unsubscribe(topic);
		}
	}
}
