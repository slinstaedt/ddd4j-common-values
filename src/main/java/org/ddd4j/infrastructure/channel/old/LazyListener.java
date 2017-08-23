package org.ddd4j.infrastructure.channel.old;

import org.ddd4j.Require;
import org.ddd4j.Throwing.Closeable;
import org.ddd4j.Throwing.TConsumer;
import org.ddd4j.Throwing.TFunction;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.Lazy;
import org.ddd4j.value.collection.Ref;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public class LazyListener<C extends ColdChannel.Callback & HotChannel.Callback>
		implements ChannelListener, PartitionRebalanceListener, Closeable {

	private class LazyColdCallback implements ColdChannel.Callback {

		@Override
		public void seek(ChannelName topic, Revision revision) {
			callbackDelegate.get().seek(topic, revision);
		}

		@Override
		public void unseek(ChannelName topic, int partition) {
			callbackDelegate.get().unseek(topic, partition);
		}
	}

	private class LazyHotCallback implements HotChannel.Callback {

		@Override
		public Promise<Integer> subscribe(ChannelName topic) {
			return callbackDelegate.get().subscribe(topic);
		}

		@Override
		public void unsubscribe(ChannelName topic) {
			callbackDelegate.get().unsubscribe(topic);
		}
	}

	private static <T> T ensureNull(T oldValue, T newValue) {
		if (oldValue == null) {
			return newValue;
		} else {
			throw new IllegalStateException("Already assigned: " + oldValue);
		}
	}

	private final Lazy<C> callbackDelegate;
	private final Ref<ColdChannel.Listener> coldDelegate;
	private final Ref<HotChannel.Listener> hotDelegate;

	public LazyListener(TFunction<LazyListener<C>, C> delegateFactory, TConsumer<C> delegateDestructor) {
		Require.nonNullElements(delegateFactory, delegateDestructor);
		this.callbackDelegate = new Lazy<>(() -> delegateFactory.apply(this), delegateDestructor);
		this.coldDelegate = Ref.createThreadsafe();
		this.hotDelegate = Ref.createThreadsafe();
	}

	public ColdChannel.Callback assign(ColdChannel.Listener listener) {
		coldDelegate.updateAndGet(l -> ensureNull(l, listener));
		return new LazyColdCallback();
	}

	public HotChannel.Callback assign(HotChannel.Listener listener) {
		hotDelegate.updateAndGet(l -> ensureNull(l, listener));
		return new LazyHotCallback();
	}

	@Override
	public void closeChecked() {
		coldDelegate.unset();
		hotDelegate.unset();
		callbackDelegate.closeChecked();
	}

	public boolean isNotBothAssigned() {
		return coldDelegate.isNull() || hotDelegate.isNull();
	}

	@Override
	public void onError(Throwable throwable) {
		coldDelegate.ifPresent(l -> l.onError(throwable));
		hotDelegate.ifPresent(l -> l.onError(throwable));
	}

	@Override
	public void onNext(ChannelName topic, Committed<ReadBuffer, ReadBuffer> committed) {
		coldDelegate.ifPresent(l -> l.onNext(topic, committed));
		hotDelegate.ifPresent(l -> l.onNext(topic, committed));
	}

	@Override
	public void onPartitionsAssigned(ChannelName topic, int[] partitions) {
		hotDelegate.ifPresent(l -> l.onPartitionsAssigned(topic, partitions));
	}

	@Override
	public void onPartitionsRevoked(ChannelName topic, int[] partitions) {
		hotDelegate.ifPresent(l -> l.onPartitionsRevoked(topic, partitions));
	}
}