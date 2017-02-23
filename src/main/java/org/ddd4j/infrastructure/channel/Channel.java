package org.ddd4j.infrastructure.channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.Throwing.Closeable;
import org.ddd4j.value.Throwing.TConsumer;
import org.ddd4j.value.math.Ordered.Comparison;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;
import org.ddd4j.value.versioned.Uncommitted;

public class Channel implements Closeable {

	public static class Callback implements ColdChannel.Callback, HotChannel.Callback {

		private final Subscriptions subscriptions;
		private final ColdChannel.Callback coldCallback;
		private final HotChannel.Callback hotCallback;

		Callback(Subscriptions subscriptions, ColdChannel.Callback coldCallback, HotChannel.Callback hotCallback) {
			this.subscriptions = Require.nonNull(subscriptions);
			this.coldCallback = Require.nonNull(coldCallback);
			this.hotCallback = Require.nonNull(hotCallback);
		}

		@Override
		public void closeChecked() throws Exception {
			subscriptions.clear();
			coldCallback.closeChecked();
			hotCallback.closeChecked();
		}

		@Override
		public void seek(ResourceDescriptor topic, Revision revision) {
			coldCallback.seek(topic, revision);
		}

		@Override
		public Promise<Integer> subscribe(ResourceDescriptor topic) {
			return subscriptions.subscribeIfNeeded(topic, t -> hotCallback.subscribe(t).sync().handleSuccess(Revisions::new));
		}

		@Override
		public void unseek(ResourceDescriptor topic, int partition) {
			coldCallback.unseek(topic, partition);
		}

		@Override
		public void unsubscribe(ResourceDescriptor topic) {
			subscriptions.unsubscribeIfNeeded(topic, () -> hotCallback.unsubscribe(topic));
		}
	}

	private static class ColdListener implements ColdChannel.Listener, Closeable {

		private final Subscriptions subscriptions;
		private final ColdChannel.Callback callback;

		private Closeable closer;

		ColdListener(Subscriptions subscriptions, ColdChannel channel) {
			this.subscriptions = Require.nonNull(subscriptions);
			this.callback = channel.register(this);
		}

		ColdChannel.Callback callback(Closeable hotCloser) {
			this.closer = Require.nonNull(hotCloser);
			return callback;
		}

		@Override
		public void closeChecked() throws Exception {
			callback.closeChecked();
		}

		@Override
		public void onError(Throwable throwable) {
			subscriptions.closeCallbacksAndDelegate(closer, throwable);
		}

		@Override
		public void onNext(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed) {
			subscriptions.onNextIfSubscribed(topic, committed, r -> {
				Revision nextExpected = committed.getNextExpected();
				if (r.compare(nextExpected) == Comparison.EQUAL) {
					callback.unseek(topic, nextExpected.getPartition());
				}
			});
		}
	}

	private static class HotListener implements HotChannel.Listener, Closeable {

		private final Subscriptions subscriptions;
		private final HotChannel.Callback callback;

		private Closeable closer;

		HotListener(Subscriptions subscriptions, HotChannel channel) {
			this.subscriptions = Require.nonNull(subscriptions);
			this.callback = channel.register(this);
		}

		HotChannel.Callback callback(Closeable coldCloser) {
			this.closer = Require.nonNull(coldCloser);
			return callback;
		}

		@Override
		public void closeChecked() throws Exception {
			callback.closeChecked();
		}

		@Override
		public void onError(Throwable throwable) {
			subscriptions.closeCallbacksAndDelegate(closer, throwable);
		}

		@Override
		public void onNext(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed) {
			subscriptions.onNextIfSubscribed(topic, committed, r -> r.update(committed.getNextExpected()));
		}

		@Override
		public void onPartitionsAssigned(ResourceDescriptor topic, int[] partitions) {
			subscriptions.onPartitionsAssigned(topic, partitions);

		}

		@Override
		public void onPartitionsRevoked(ResourceDescriptor topic, int[] partitions) {
			subscriptions.onPartitionsRevoked(topic, partitions);
		}
	}

	public interface Listener extends ChannelListener, PartitionRebalanceListener {
	}

	private static class Subscriptions {

		private final Listener listener;
		private final Map<ResourceDescriptor, Promise<Revisions>> hotRevisions;

		Subscriptions(Listener listener) {
			this.listener = Require.nonNull(listener);
			this.hotRevisions = new ConcurrentHashMap<>();
		}

		public void clear() {
			hotRevisions.clear();
		}

		void closeCallbacksAndDelegate(Closeable otherCallback, Throwable throwable) {
			if (otherCallback != null) {
				otherCallback.close();
			}
			hotRevisions.clear();
			listener.onError(throwable);
		}

		void onNextIfSubscribed(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed, TConsumer<Revisions> action) {
			Promise<Revisions> promise = hotRevisions.get(topic);
			if (promise != null) {
				promise.whenCompleteSuccessfully(action);
				listener.onNext(topic, committed);
			}
		}

		void onPartitionsAssigned(ResourceDescriptor topic, int[] partitions) {
			listener.onPartitionsAssigned(topic, partitions);
		}

		void onPartitionsRevoked(ResourceDescriptor topic, int[] partitions) {
			listener.onPartitionsRevoked(topic, partitions);
		}

		Promise<Integer> subscribeIfNeeded(ResourceDescriptor topic, Function<ResourceDescriptor, Promise<Revisions>> subscriber) {
			return hotRevisions.computeIfAbsent(topic, subscriber).handleSuccess(Revisions::getPartitionSize);
		}

		void unsubscribeIfNeeded(ResourceDescriptor topic, Runnable unsubscriber) {
			if (hotRevisions.remove(topic) != null) {
				unsubscriber.run();
			}
		}
	}

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
		return new Callback(subscriptions, coldListener.callback(hotListener), hotListener.callback(coldListener));
	}

	private void send(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed) {
		hotChannel.send(topic, committed);
	}

	public Promise<CommitResult<ReadBuffer, ReadBuffer>> trySend(ResourceDescriptor topic, Uncommitted<ReadBuffer, ReadBuffer> attempt) {
		Promise<CommitResult<ReadBuffer, ReadBuffer>> promise = coldChannel.trySend(topic, attempt);
		return promise.whenCompleteSuccessfully(r -> r.visitCommitted(c -> hotChannel.send(topic, c)));
	}
}
