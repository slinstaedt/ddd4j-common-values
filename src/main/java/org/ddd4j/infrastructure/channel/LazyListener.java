package org.ddd4j.infrastructure.channel;

import java.util.Objects;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.Throwing.Closeable;
import org.ddd4j.value.collection.Ref;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public class LazyListener<C extends ColdChannel.Callback & HotChannel.Callback> implements ChannelListener, PartitionRebalanceListener {

	private class ColdCallback implements ColdChannel.Callback {

		private final ColdChannel.Callback delegate;

		ColdCallback(ColdChannel.Callback delegate) {
			this.delegate = Require.nonNull(delegate);
		}

		@Override
		public void closeChecked() throws Exception {
			coldDelegate.unset();
			checkCloseCallbackDelegate();
		}

		@Override
		public void seek(ResourceDescriptor topic, Revision revision) {
			delegate.seek(topic, revision);
		}

		@Override
		public void unseek(ResourceDescriptor topic, int partition) {
			delegate.unseek(topic, partition);
		}
	}

	private class HotCallback implements HotChannel.Callback {

		private final HotChannel.Callback delegate;

		HotCallback(HotChannel.Callback delegate) {
			this.delegate = Require.nonNull(delegate);
		}

		@Override
		public void closeChecked() throws Exception {
			hotDelegate.unset();
			checkCloseCallbackDelegate();
		}

		@Override
		public Promise<Integer> subscribe(ResourceDescriptor topic) {
			return delegate.subscribe(topic);
		}

		@Override
		public void unsubscribe(ResourceDescriptor topic) {
			delegate.unsubscribe(topic);
		}
	}

	private static <T> T ensureNull(T oldValue, T newValue) {
		if (oldValue == null) {
			return newValue;
		} else {
			throw new IllegalStateException("Already assigned: " + oldValue);
		}
	}

	private final Function<LazyListener<C>, C> delegateFactory;
	private final Ref<C> callbackDelegate;
	private final Ref<ColdChannel.Listener> coldDelegate;
	private final Ref<HotChannel.Listener> hotDelegate;

	public LazyListener(Function<LazyListener<C>, C> delegateFactory) {
		this.delegateFactory = Require.nonNull(delegateFactory);
		this.callbackDelegate = Ref.createThreadsafe();
		this.coldDelegate = Ref.createThreadsafe();
		this.hotDelegate = Ref.createThreadsafe();
	}

	public void assign(ColdChannel.Listener listener) {
		coldDelegate.updateAndGet(l -> ensureNull(l, listener));
		listener.onRegistration(new ColdCallback(callback()));
	}

	public void assign(HotChannel.Listener listener) {
		hotDelegate.updateAndGet(l -> ensureNull(l, listener));
		listener.onRegistration(new HotCallback(callback()));
	}

	private C callback() {
		return callbackDelegate.updateAndGet(() -> delegateFactory.apply(this), Objects::isNull);
	}

	private synchronized void checkCloseCallbackDelegate() {
		if (coldDelegate.isNull() && hotDelegate.isNull()) {
			callbackDelegate.ifPresent(Closeable::close);
			callbackDelegate.unset();
		}
	}

	@Override
	public void onPartitionsAssigned(ResourceDescriptor topic, int[] partitions) {
		hotDelegate.ifPresent(l -> l.onPartitionsAssigned(topic, partitions));
	}

	@Override
	public void onPartitionsRevoked(ResourceDescriptor topic, int[] partitions) {
		hotDelegate.ifPresent(l -> l.onPartitionsRevoked(topic, partitions));
	}

	@Override
	public void onError(Throwable throwable) {
		coldDelegate.ifPresent(l -> l.onError(throwable));
		hotDelegate.ifPresent(l -> l.onError(throwable));
	}

	@Override
	public void onNext(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed) {
		coldDelegate.ifPresent(l -> l.onNext(topic, committed));
		hotDelegate.ifPresent(l -> l.onNext(topic, committed));
	}
}