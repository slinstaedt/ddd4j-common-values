package org.ddd4j.repository.log;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.ddd4j.Require;
import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.SchemaCodec;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.RepartitioningListener;
import org.ddd4j.infrastructure.channel.spi.DataAccessFactory;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelPartition;
import org.ddd4j.infrastructure.domain.value.ChannelSpec;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.repository.ReactiveListener;
import org.ddd4j.value.versioned.Committed;

public class LogPublisher<C> implements CommitListener<ReadBuffer, ReadBuffer>, ErrorListener, RepartitioningListener {

	public interface Listener extends CommitListener<ReadBuffer, ReadBuffer>, ErrorListener, RepartitioningListener {

		class Mapped<C> implements Listener {

			private final CommitListener<ReadBuffer, ReadBuffer> commit;
			private final ErrorListener error;
			private final C callback;
			private final Mapper<C> onAssigned;
			private final Mapper<C> onRevoked;

			public Mapped(CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error, C callback, Mapper<C> onAssigned,
					Mapper<C> onRevoked) {
				this.commit = Require.nonNull(commit);
				this.error = Require.nonNull(error);
				this.callback = Require.nonNull(callback);
				this.onAssigned = Require.nonNull(onAssigned);
				this.onRevoked = Require.nonNull(onRevoked);
			}

			@Override
			public void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
				commit.onNext(name, committed);
			}

			@Override
			public void onError(Throwable throwable) {
				error.onError(throwable);
			}

			@Override
			public Promise<?> onPartitionsAssigned(Sequence<ChannelPartition> partitions) {
				return onAssigned.apply(callback, partitions);
			}

			@Override
			public Promise<?> onPartitionsRevoked(Sequence<ChannelPartition> partitions) {
				return onRevoked.apply(callback, partitions);
			}
		}
	}

	public interface ListenerFactory<C> {

		Listener create(CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error, C callback);

		default ListenerFactory<C> wrap(UnaryOperator<Listener> wrapper) {
			Require.nonNull(wrapper);
			return (commit, error, callback) -> wrapper.apply(create(commit, error, callback));
		}
	}

	private static class Subscriptions {

		private final Promise<Integer> partitionSize;
		private final Consumer<ChannelName> onUnsubscribed;
		private final Map<CommitListener<?, ?>, Listener> listeners;

		Subscriptions(Promise<Integer> partitionSize, Consumer<ChannelName> onUnsubscribed) {
			this.partitionSize = Require.nonNull(partitionSize);
			this.onUnsubscribed = Require.nonNull(onUnsubscribed);
			this.listeners = new ConcurrentHashMap<>();
		}

		Subscriptions add(CommitListener<?, ?> handle, Listener listener) {
			listeners.put(handle, listener);
			return this;
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

		Subscriptions remove(ChannelName name, CommitListener<?, ?> handle) {
			if (listeners.remove(handle) != null && listeners.isEmpty()) {
				onUnsubscribed.accept(name);
				return null;
			} else {
				return this;
			}
		}
	}

	private static final Subscriptions NONE = new Subscriptions(Promise.failed(new AssertionError()), ChannelName::getClass);

	private final ListenerFactory<C> factory;
	private final Function<ChannelName, Subscriptions> onSubscribed;
	private final ConcurrentMap<ChannelName, Subscriptions> subscriptions;

	public LogPublisher(ListenerFactory<C> factory, Function<ChannelName, Promise<Integer>> onSubscribed,
			Consumer<ChannelName> onUnsubscribed) {
		Require.nonNullElements(factory, onSubscribed, onUnsubscribed);
		this.factory = Require.nonNull(factory);
		this.onSubscribed = name -> new Subscriptions(onSubscribed.apply(name), onUnsubscribed);
		this.subscriptions = new ConcurrentHashMap<>();
	}

	public Set<ChannelName> getSubscribedChannels() {
		return Collections.unmodifiableSet(subscriptions.keySet());
	}

	public boolean isSubcribed() {
		return !subscriptions.isEmpty();
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

	public Promise<Integer> subscribe(ChannelName name, CommitListener<?, ?> handle, CommitListener<ReadBuffer, ReadBuffer> commit,
			ErrorListener error, C callback) {
		Listener listener = factory.create(commit, error, callback);
		return subscriptions.computeIfAbsent(name, onSubscribed).add(handle, listener).partitionSize();
	}

	public Promise<Integer> subscribe(ChannelName name, CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error, C callback) {
		return subscribe(name, commit, commit, error, callback);
	}

	public <K, V> void subscribe(SchemaCodec.Factory factory, ChannelSpec<K, V> spec, CommitListener<K, V> commit, ErrorListener error,
			C callback) {
		SchemaCodec.Decoder<V> decoder = factory.decoder(spec);
		CommitListener<ReadBuffer, ReadBuffer> mappedListener = commit.mapPromised(spec::deserializeKey, decoder::decode, error);
		subscribe(spec.getName(), commit, mappedListener, error, callback);
	}

	public Flow.Publisher<Committed<ReadBuffer, ReadBuffer>> subscriber(ChannelName name, C callback) {
		Require.nonNullElements(name, callback);
		return s -> {
			ReactiveListener<ReadBuffer, ReadBuffer> listener = new ReactiveListener<>(s, unsubscriber(name));
			subscribe(name, listener, listener, callback);
		};
	}

	public void unsubscribe(ChannelName name, CommitListener<?, ?> handle) {
		subscriptions.computeIfPresent(name, (n, ls) -> ls.remove(n, handle));
	}

	public Consumer<? super CommitListener<?, ?>> unsubscriber(ChannelName name) {
		Require.nonNull(name);
		return l -> unsubscribe(name, l);
	}
}
