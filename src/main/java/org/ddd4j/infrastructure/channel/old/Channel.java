package org.ddd4j.infrastructure.channel.old;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.Throwing.Closeable;
import org.ddd4j.Throwing.TConsumer;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Key;
import org.ddd4j.value.math.Ordered.Comparison;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;
import org.ddd4j.value.versioned.Uncommitted;

public class Channel implements Closeable {

	public static class Callback implements HotChannel.Callback {

		private final Subscriptions subscriptions;
		private final ColdChannel.Callback coldCallback;
		private final HotChannel.Callback hotCallback;

		Callback(Subscriptions subscriptions, ColdChannel.Callback coldCallback, HotChannel.Callback hotCallback) {
			this.subscriptions = Require.nonNull(subscriptions);
			this.coldCallback = Require.nonNull(coldCallback);
			this.hotCallback = Require.nonNull(hotCallback);
		}

		public void seek(ChannelName topic, Revision revision) {
			coldCallback.seek(topic, revision);
		}

		@Override
		public Promise<Integer> subscribe(ChannelName topic) {
			return subscriptions.subscribeIfNeeded(topic, t -> hotCallback.subscribe(t).sync().thenApply(Revisions::new));
		}

		@Override
		public void unsubscribe(ChannelName topic) {
			subscriptions.unsubscribeIfNeeded(topic, hotCallback::unsubscribe);
		}
	}

	private static class ColdListener implements ColdChannel.Listener {

		private final Subscriptions subscriptions;
		private final ColdChannel.Callback callback;

		private Closeable closer;

		ColdListener(Subscriptions subscriptions, ColdChannel channel) {
			this.subscriptions = Require.nonNull(subscriptions);
			this.callback = channel.register(this);
		}

		@Override
		public void onError(Throwable throwable) {
			subscriptions.closeCallbacksAndDelegate(closer, throwable);
		}

		@Override
		public void onNext(ChannelName topic, Committed<ReadBuffer, ReadBuffer> committed) {
			subscriptions.onNextIfSubscribed(topic, committed, r -> {
				Revision nextExpected = committed.getNextExpected();
				if (r.compare(nextExpected) == Comparison.EQUAL) {
					callback.unseek(topic, nextExpected.getPartition());
				}
			});
		}
	}

	private static class HotListener implements HotChannel.Listener {

		private final Subscriptions subscriptions;
		private final HotChannel.Callback callback;

		private Closeable closer;

		HotListener(Subscriptions subscriptions, HotChannel channel) {
			this.subscriptions = Require.nonNull(subscriptions);
			this.callback = channel.register(this);
		}

		@Override
		public void onError(Throwable throwable) {
			subscriptions.closeCallbacksAndDelegate(closer, throwable);
		}

		@Override
		public void onNext(ChannelName topic, Committed<ReadBuffer, ReadBuffer> committed) {
			subscriptions.onNextIfSubscribed(topic, committed, r -> r.update(committed.getNextExpected()));
		}

		@Override
		public void onPartitionsAssigned(ChannelName topic, int[] partitions) {
			subscriptions.onPartitionsAssigned(topic, partitions);

		}

		@Override
		public void onPartitionsRevoked(ChannelName topic, int[] partitions) {
			subscriptions.onPartitionsRevoked(topic, partitions);
		}
	}

	public interface Listener extends ChannelListener, PartitionRebalanceListener {
	}

	private static class Subscriptions {

		private final Listener listener;
		private final Map<ChannelName, Promise<Revisions>> hotRevisions;

		Subscriptions(Listener listener) {
			this.listener = Require.nonNull(listener);
			this.hotRevisions = new ConcurrentHashMap<>();
		}

		void closeCallbacksAndDelegate(Closeable otherCallback, Throwable throwable) {
			if (otherCallback != null) {
				otherCallback.close();
			}
			hotRevisions.clear();
			listener.onError(throwable);
		}

		void onNextIfSubscribed(ChannelName topic, Committed<ReadBuffer, ReadBuffer> committed, TConsumer<Revisions> action) {
			Promise<Revisions> promise = hotRevisions.get(topic);
			if (promise != null) {
				promise.whenCompleteSuccessfully(action);
				listener.onNext(topic, committed);
			}
		}

		void onPartitionsAssigned(ChannelName topic, int[] partitions) {
			listener.onPartitionsAssigned(topic, partitions);
		}

		void onPartitionsRevoked(ChannelName topic, int[] partitions) {
			listener.onPartitionsRevoked(topic, partitions);
		}

		Promise<Integer> subscribeIfNeeded(ChannelName topic, Function<ChannelName, Promise<Revisions>> subscriber) {
			return hotRevisions.computeIfAbsent(topic, subscriber).thenApply(Revisions::getPartitionSize);
		}

		void unsubscribeIfNeeded(ChannelName topic, Consumer<ChannelName> unsubscriber) {
			if (hotRevisions.remove(topic) != null) {
				unsubscriber.accept(topic);
			}
		}
	}

	public static final Key<Channel> KEY = Key.of(Channel.class);

	private final ColdChannel coldChannel;
	private final HotChannel hotChannel;

	public Channel(ColdChannel coldChannel, HotChannel hotChannel) {
		this.coldChannel = Require.nonNull(coldChannel);
		this.hotChannel = Require.nonNull(hotChannel);
	}

	@Override
	public void closeChecked() throws Exception {
		coldChannel.closeChecked();
		hotChannel.closeChecked();
	}

	// TODO allow multiple registrations?
	public synchronized Callback register(Listener listener) {
		Subscriptions subscriptions = new Subscriptions(listener);
		ColdListener coldListener = new ColdListener(subscriptions, coldChannel);
		HotListener hotListener = new HotListener(subscriptions, hotChannel);
		return new Callback(subscriptions, coldListener.callback, hotListener.callback);
	}

	// TODO allow sending with locking?
	private void send(ChannelName topic, Committed<ReadBuffer, ReadBuffer> committed) {
		hotChannel.send(topic, committed);
	}

	public Promise<CommitResult<ReadBuffer, ReadBuffer>> trySend(ChannelName topic, Uncommitted<ReadBuffer, ReadBuffer> attempt) {
		Promise<CommitResult<ReadBuffer, ReadBuffer>> promise = coldChannel.trySend(topic, attempt);
		return promise.whenCompleteSuccessfully(r -> r.onCommitted(c -> hotChannel.send(topic, c)));
	}
}
