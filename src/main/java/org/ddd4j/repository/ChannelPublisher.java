package org.ddd4j.repository;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.SchemaCodec;
import org.ddd4j.infrastructure.channel.api.CompletionListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.RepartitioningListener;
import org.ddd4j.infrastructure.channel.api.SourceListener;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.infrastructure.channel.domain.ChannelPartition;
import org.ddd4j.infrastructure.channel.domain.ChannelSpec;
import org.ddd4j.infrastructure.channel.spi.DataAccessFactory;
import org.ddd4j.infrastructure.channel.spi.HotSource;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;

public class ChannelPublisher implements SourceListener<ReadBuffer, ReadBuffer>, ErrorListener, HotSource.Callback {

	private static class Listener {

		private final SourceListener<ReadBuffer, ReadBuffer> source;
		private final CompletionListener completion;
		private final ErrorListener error;
		private final RepartitioningListener repartitioning;

		Listener(SourceListener<ReadBuffer, ReadBuffer> source, CompletionListener completion, ErrorListener error,
				RepartitioningListener repartitioning) {
			this.source = Require.nonNull(source);
			this.completion = Require.nonNull(completion);
			this.error = Require.nonNull(error);
			this.repartitioning = Require.nonNull(repartitioning);
		}

		void onComplete() {
			completion.onComplete();
		}

		void onError(Throwable throwable) {
			error.onError(throwable);
		}

		void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
			source.onNext(name, committed);
		}

		Promise<?> onPartitionsAssigned(Sequence<ChannelPartition> partitions) {
			return repartitioning.onPartitionsAssigned(partitions);
		}

		Promise<?> onPartitionsRevoked(Sequence<ChannelPartition> partitions) {
			return repartitioning.onPartitionsRevoked(partitions);
		}
	}

	private static class Listeners {

		private final Promise<Integer> partitionSize;
		private final Consumer<ChannelName> onUnsubscribed;
		private final Map<SourceListener<?, ?>, Agent<Listener>> listeners;

		Listeners(Promise<Integer> partitionSize, Consumer<ChannelName> onUnsubscribed) {
			this.partitionSize = Require.nonNull(partitionSize);
			this.onUnsubscribed = Require.nonNull(onUnsubscribed);
			this.listeners = new ConcurrentHashMap<>();
		}

		Listeners add(SourceListener<?, ?> handle, Agent<Listener> listener) {
			listeners.put(handle, listener);
			return this;
		}

		void onComplete() {
			listeners.values().forEach(a -> a.execute(l -> l.onComplete()));
		}

		void onError(Throwable throwable) {
			listeners.values().forEach(a -> a.execute(l -> l.onError(throwable)));
			listeners.clear();
		}

		void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
			listeners.values().forEach(a -> a.execute(l -> l.onNext(name, DataAccessFactory.resetBuffers(committed))));
		}

		Promise<?> onPartitionsAssigned(Sequence<ChannelPartition> partitions) {
			return Promise.completed()
					.runAfterAll(listeners.values().stream().map(a -> a.performFlat(l -> l.onPartitionsAssigned(partitions))));
		}

		Promise<?> onPartitionsRevoked(Sequence<ChannelPartition> partitions) {
			return Promise.completed()
					.runAfterAll(listeners.values().stream().map(a -> a.performFlat(l -> l.onPartitionsRevoked(partitions))));
		}

		Promise<Integer> partitionSize() {
			return partitionSize;
		}

		Listeners remove(ChannelName name, SourceListener<?, ?> handle) {
			if (listeners.remove(handle) != null && listeners.isEmpty()) {
				onUnsubscribed.accept(name);
				return null;
			} else {
				return this;
			}
		}
	}

	private static class Subscriptions {

		private static final Listeners NONE = new Listeners(Promise.failed(new AssertionError()), ChannelName::getClass);

		private final Function<ChannelName, Listeners> onSubscribed;
		private final ConcurrentMap<ChannelName, Listeners> listeners;

		Subscriptions(Function<ChannelName, Listeners> onSubscribed) {
			this.onSubscribed = Require.nonNull(onSubscribed);
			this.listeners = new ConcurrentHashMap<>();
		}

		Set<ChannelName> channels() {
			return Collections.unmodifiableSet(listeners.keySet());
		}

		boolean isEmpty() {
			return listeners.isEmpty();
		}

		void onComplete(ChannelName name) {
			listeners.getOrDefault(name, NONE).onComplete();
		}

		void onError(Throwable throwable) {
			listeners.values().forEach(l -> l.onError(throwable));
			listeners.clear();
		}

		void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
			listeners.getOrDefault(name, NONE).onNext(name, committed);
		}

		Promise<?> onPartitionsAssigned(Sequence<ChannelPartition> partitions) {
			return Promise.completed().runAfterAll(partitions.groupBy(ChannelPartition::getName).entrySet().stream().map(
					e -> listeners.getOrDefault(e.getKey(), NONE).onPartitionsAssigned(e.getValue())));
		}

		Promise<?> onPartitionsRevoked(Sequence<ChannelPartition> partitions) {
			return Promise.completed().runAfterAll(partitions.groupBy(ChannelPartition::getName).entrySet().stream().map(
					e -> listeners.getOrDefault(e.getKey(), NONE).onPartitionsRevoked(e.getValue())));
		}

		Promise<Integer> subscribe(ChannelName name, SourceListener<?, ?> handle, Agent<Listener> listener) {
			return listeners.computeIfAbsent(name, onSubscribed).add(handle, listener).partitionSize();
		}

		void unsubscribe(ChannelName name, SourceListener<?, ?> listener) {
			listeners.computeIfPresent(name, (n, ls) -> ls.remove(n, listener));
		}
	}

	private final Scheduler scheduler;
	private final Subscriptions subscriptions;

	public ChannelPublisher(Scheduler scheduler, Function<ChannelName, Promise<Integer>> onSubscribed,
			Consumer<ChannelName> onUnsubscribed) {
		Require.nonNullElements(onSubscribed, onUnsubscribed);
		this.scheduler = Require.nonNull(scheduler);
		this.subscriptions = new Subscriptions(name -> new Listeners(onSubscribed.apply(name), onUnsubscribed));
	}

	public boolean isSubcribed() {
		return !subscriptions.isEmpty();
	}

	@Override
	public void onError(Throwable throwable) {
		subscriptions.onError(throwable);
	}

	@Override
	public void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
		subscriptions.onNext(name, committed);
	}

	@Override
	public Promise<?> onPartitionsAssigned(Sequence<ChannelPartition> partitions) {
		return subscriptions.onPartitionsAssigned(partitions);
	}

	@Override
	public Promise<?> onPartitionsRevoked(Sequence<ChannelPartition> partitions) {
		return subscriptions.onPartitionsRevoked(partitions);
	}

	<L extends SourceListener<ReadBuffer, ReadBuffer> & CompletionListener & ErrorListener & RepartitioningListener> void subscribe(
			ChannelName name, L listener) {
		subscribe(name, listener, listener, listener, listener, listener);
	}

	private Promise<Integer> subscribe(ChannelName name, SourceListener<?, ?> handle, SourceListener<ReadBuffer, ReadBuffer> source,
			CompletionListener completion, ErrorListener error, RepartitioningListener repartitioning) {
		Agent<Listener> listener = scheduler.createAgent(new Listener(source, completion, error, repartitioning));
		return subscriptions.subscribe(name, handle, listener);
	}

	public Promise<Integer> subscribe(ChannelName name, SourceListener<ReadBuffer, ReadBuffer> source, CompletionListener completion,
			ErrorListener error, RepartitioningListener repartitioning) {
		return subscribe(name, source, source, completion, error, repartitioning);
	}

	public <K, V> void subscribe(SchemaCodec.Factory factory, ChannelSpec<K, V> spec, SourceListener<K, V> source,
			CompletionListener completion, ErrorListener error, RepartitioningListener repartitioning) {
		SchemaCodec.Decoder<V> decoder = factory.decoder(spec);
		SourceListener<ReadBuffer, ReadBuffer> mappedListener = source.mapPromised(spec::deserializeKey, decoder::decode, error);
		subscribe(spec.getName(), source, mappedListener, completion, error, repartitioning);
	}

	public Set<ChannelName> subscribed() {
		return subscriptions.channels();
	}

	public Publisher<Committed<ReadBuffer, ReadBuffer>> subscriber(ChannelName name) {
		Require.nonNull(name);
		return s -> subscribe(name, new ReactiveListener<>(s, unsubscriber(name)));
	}

	public void unsubscribe(ChannelName name, SourceListener<?, ?> listener) {
		subscriptions.unsubscribe(name, listener);
	}

	public Consumer<? super SourceListener<?, ?>> unsubscriber(ChannelName name) {
		Require.nonNull(name);
		return l -> unsubscribe(name, l);
	}
}
