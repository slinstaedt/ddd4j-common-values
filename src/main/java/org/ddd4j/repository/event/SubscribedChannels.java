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
import org.ddd4j.infrastructure.channel.spi.DataAccessFactory;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelPartition;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Sequence;
import org.ddd4j.value.versioned.Committed;

public class SubscribedChannels implements ChannelListener {

	private static class Subscriptions {

		private final Promise<Integer> partitionSize;
		private final Runnable onUnsubscribed;
		private final Map<Object, ChannelListener> listeners;

		Subscriptions(Promise<Integer> partitionSize, Runnable onUnsubscribed) {
			this.partitionSize = Require.nonNull(partitionSize);
			this.onUnsubscribed = Require.nonNull(onUnsubscribed);
			this.listeners = new ConcurrentHashMap<>();
		}

		SubscribedChannels.Subscriptions add(Object handle, ChannelListener listener) {
			listeners.putIfAbsent(handle, listener);
			return this;
		}

		void closeAll() {
			listeners.values().forEach(Closeable::close);
		}

		void onError(Throwable throwable) {
			listeners.values().forEach(l -> l.onError(throwable));
			listeners.clear();
		}

		void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
			listeners.values().forEach(l -> l.onNext(name, DataAccessFactory.resetBuffers(committed)));
		}

		Promise<?> onPartitionsAssigned(Sequence<ChannelPartition> partitions) {
			return Promise.completed().runAfterAll(listeners.values().stream().map(l -> l.onPartitionsAssigned(partitions)));
		}

		Promise<?> onPartitionsRevoked(Sequence<ChannelPartition> partitions) {
			return Promise.completed().runAfterAll(listeners.values().stream().map(l -> l.onPartitionsRevoked(partitions)));
		}

		Promise<Integer> partitionSize() {
			return partitionSize;
		}

		SubscribedChannels.Subscriptions remove(Object handle) {
			if (listeners.remove(handle) != null && listeners.isEmpty()) {
				onUnsubscribed.run();
				return null;
			} else {
				return this;
			}
		}
	}

	private static final SubscribedChannels.Subscriptions NONE = new Subscriptions(Promise.failed(new AssertionError()),
			Throwing.Task.NONE);

	private final Function<ChannelName, SubscribedChannels.Subscriptions> onSubscribed;
	private final Map<ChannelName, SubscribedChannels.Subscriptions> subscriptions;

	public SubscribedChannels(Function<ChannelName, Promise<Integer>> onSubscribed, Consumer<ChannelName> onUnsubscribed) {
		Require.nonNullElements(onSubscribed, onUnsubscribed);
		this.onSubscribed = name -> new Subscriptions(onSubscribed.apply(name), () -> onUnsubscribed.accept(name));
		this.subscriptions = new ConcurrentHashMap<>();
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
	public void onError(Throwable throwable) {
		subscriptions.values().forEach(l -> l.onError(throwable));
		subscriptions.clear();
	}

	@Override
	public void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
		subscriptions.getOrDefault(name, NONE).onNext(name, committed);
	}

	@Override
	public Promise<?> onPartitionsAssigned(Sequence<ChannelPartition> partitions) {
		return Promise.completed().runAfterAll(partitions.groupBy(ChannelPartition::getName).entrySet().stream().map(
				e -> subscriptions.getOrDefault(e.getKey(), NONE).onPartitionsAssigned(e.getValue())));
	}

	@Override
	public Promise<?> onPartitionsRevoked(Sequence<ChannelPartition> partitions) {
		return Promise.completed().runAfterAll(partitions.groupBy(ChannelPartition::getName).entrySet().stream().map(
				e -> subscriptions.getOrDefault(e.getKey(), NONE).onPartitionsRevoked(e.getValue())));
	}

	public Promise<Integer> subscribe(ChannelName name, Object handle, ChannelListener listener) {
		return subscriptions.computeIfAbsent(name, onSubscribed).add(handle, listener).partitionSize();
	}

	public void unsubscribe(ChannelName name, Object handle) {
		subscriptions.computeIfPresent(name, (n, s) -> s.remove(handle));
	}
}