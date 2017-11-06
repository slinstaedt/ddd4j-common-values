package org.ddd4j.repository.event;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
import org.ddd4j.Throwing.Closeable;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.RebalanceListener;
import org.ddd4j.infrastructure.channel.spi.DataAccessFactory;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelPartition;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Sequence;
import org.ddd4j.value.versioned.Committed;

public class SubscribedChannels implements CommitListener<ReadBuffer, ReadBuffer>, ErrorListener, RebalanceListener, Closeable {

	private static class Subscriptions {

		private final Promise<Integer> partitionSize;
		private final Runnable onUnsubscribed;
		private final Map<Object, ChannelListener> listeners;

		Subscriptions(Promise<Integer> partitionSize, Runnable onUnsubscribed) {
			this.partitionSize = Require.nonNull(partitionSize);
			this.onUnsubscribed = Require.nonNull(onUnsubscribed);
			this.listeners = new ConcurrentHashMap<>(INITIAL_CAPACITY);
		}

		Promise<Integer> add(Object handle, ChannelListener listener) {
			Require.nonNullElements(handle, listener);
			if (listeners.putIfAbsent(handle, listener) != null) {
				listener.onError(new IllegalStateException("Already subscribed: " + handle));
			}
			return partitionSize;
		}

		void closeAll() {
			listeners.values().forEach(Closeable::close);
		}

		Promise<?> onError(Throwable throwable) {
			return Promise.completed().runAfterAll(listeners.values().stream().map(l -> l.onError(throwable))).whenComplete(
					listeners::clear);
		}

		Promise<?> onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
			return Promise.completed()
					.runAfterAll(listeners.values().stream().map(l -> l.onNext(name, DataAccessFactory.resetBuffers(committed))));
		}

		Promise<?> onRebalance(Mode mode, Sequence<ChannelPartition> partitions) {
			return Promise.completed().runAfterAll(listeners.values().stream().map(l -> l.onRebalance(mode, partitions)));
		}

		Subscriptions remove(Object handle) {
			if (listeners.remove(handle) != null && listeners.isEmpty()) {
				onUnsubscribed.run();
				return null;
			} else {
				return this;
			}
		}
	}

	private static final int INITIAL_CAPACITY = 4;
	private static final Subscriptions NONE = new Subscriptions(Promise.failed(new AssertionError()), Throwing.Task.NONE);

	private final Function<ChannelName, Subscriptions> onSubscribed;
	private final Map<ChannelName, Subscriptions> subscriptions;

	public SubscribedChannels(Function<ChannelName, Promise<Integer>> onSubscribed, Consumer<ChannelName> onUnsubscribed) {
		Require.nonNullElements(onSubscribed, onUnsubscribed);
		this.onSubscribed = name -> new Subscriptions(onSubscribed.apply(name), () -> onUnsubscribed.accept(name));
		this.subscriptions = new ConcurrentHashMap<>(INITIAL_CAPACITY);
	}

	@Override
	public void closeChecked() throws Exception {
		subscriptions.values().forEach(Subscriptions::closeAll);
		subscriptions.clear();
	}

	public Set<ChannelName> getNames() {
		return Collections.unmodifiableSet(subscriptions.keySet());
	}

	@Override
	public Promise<?> onError(Throwable throwable) {
		return Promise.completed().runAfterAll(subscriptions.values().stream().map(s -> s.onError(throwable))).whenComplete(
				subscriptions::clear);
	}

	@Override
	public Promise<?> onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
		return subscriptions.getOrDefault(name, NONE).onNext(name, committed);
	}

	@Override
	public Promise<?> onRebalance(Mode mode, Sequence<ChannelPartition> partitions) {
		return Promise.completed().runAfterAll(partitions.groupBy(ChannelPartition::getName).entrySet().stream().map(
				e -> subscriptions.getOrDefault(e.getKey(), NONE).onRebalance(mode, e.getValue())));
	}

	public Promise<Integer> subscribe(ChannelName name, Object handle, ChannelListener listener) {
		Require.nonNullElements(name, handle, listener);
		return subscriptions.computeIfAbsent(name, onSubscribed).add(handle, listener);
	}

	public void unsubscribe(ChannelName name, Object handle) {
		Require.nonNullElements(name, handle);
		subscriptions.computeIfPresent(name, (n, s) -> s.remove(handle));
	}
}