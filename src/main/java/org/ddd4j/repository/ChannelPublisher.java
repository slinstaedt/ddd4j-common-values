package org.ddd4j.repository;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.SchemaCodec;
import org.ddd4j.infrastructure.channel.SchemaCodec.Decoder;
import org.ddd4j.infrastructure.channel.api.CompletionListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.SourceListener;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.infrastructure.channel.domain.ChannelSpec;
import org.ddd4j.infrastructure.channel.spi.DataAccessFactory;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;
import org.reactivestreams.Publisher;

public class ChannelPublisher {

	private static class Listener {

		private final SourceListener<ReadBuffer, ReadBuffer> source;
		private final CompletionListener completion;
		private final ErrorListener error;

		Listener(SourceListener<ReadBuffer, ReadBuffer> source, CompletionListener completion, ErrorListener error) {
			this.source = Require.nonNull(source);
			this.completion = Require.nonNull(completion);
			this.error = Require.nonNull(error);
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
	}

	private static class Listeners {

		private final ChannelName name;
		private final Consumer<ChannelName> closer;
		private final Promise<Integer> partitionSize;
		private final Map<SourceListener<?, ?>, Agent<Listener>> listeners;

		Listeners(ChannelName name, Function<ChannelName, Promise<Integer>> onSubscribed, Consumer<ChannelName> onUnsubscribed) {
			this.name = Require.nonNull(name);
			this.closer = Require.nonNull(onUnsubscribed);
			this.partitionSize = onSubscribed.apply(name);
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
		}

		void onNext(Committed<ReadBuffer, ReadBuffer> committed) {
			listeners.values().forEach(a -> a.execute(l -> l.onNext(name, DataAccessFactory.resetBuffers(committed))));
		}

		Promise<Integer> partitionSize() {
			return partitionSize;
		}

		Listeners remove(SourceListener<?, ?> handle) {
			if (listeners.remove(handle) != null && listeners.isEmpty()) {
				closer.accept(name);
				return null;
			} else {
				return this;
			}
		}
	}

	private static class Subscriptions {

		private static final Listeners NONE = new Listeners(ChannelName.of("<NONE>"), n -> Promise.failed(new AssertionError()),
				ChannelName::getClass);

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

		void onError(ChannelName name, Throwable throwable) {
			listeners.getOrDefault(name, NONE).onError(throwable);
		}

		void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
			listeners.getOrDefault(name, NONE).onNext(committed);
		}

		Promise<Integer> subscribe(ChannelName name, SourceListener<?, ?> handle, Agent<Listener> listener) {
			return listeners.computeIfAbsent(name, onSubscribed).add(handle, listener).partitionSize();
		}

		void unsubscribe(ChannelName name, SourceListener<?, ?> listener) {
			listeners.computeIfPresent(name, (n, l) -> l.remove(listener));
		}
	}

	private final Scheduler scheduler;
	private final Subscriptions subscriptions;

	public ChannelPublisher(Scheduler scheduler, Function<ChannelName, Promise<Integer>> onSubscribed,
			Consumer<ChannelName> onUnsubscribed) {
		Require.nonNullElements(onSubscribed, onUnsubscribed);
		this.scheduler = Require.nonNull(scheduler);
		this.subscriptions = new Subscriptions(name -> new Listeners(name, onSubscribed, onUnsubscribed));
	}

	public boolean isSubcribed() {
		return !subscriptions.isEmpty();
	}

	public void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
		subscriptions.onNext(name, committed);
	}

	public Publisher<Committed<ReadBuffer, ReadBuffer>> publisher(ChannelName name) {
		Require.nonNull(name);
		return s -> {
			ReactiveListener<ReadBuffer, ReadBuffer> listener = new ReactiveListener<>(s, unsubscriber(name));
			subscribe(name, listener, listener, listener, listener);
		};
	}

	private Promise<Integer> subscribe(ChannelName name, SourceListener<?, ?> handle, SourceListener<ReadBuffer, ReadBuffer> source,
			CompletionListener completion, ErrorListener error) {
		Agent<Listener> listener = scheduler.createAgent(new Listener(source, completion, error));
		return subscriptions.subscribe(name, handle, listener);
	}

	public Promise<Integer> subscribe(ChannelName name, SourceListener<ReadBuffer, ReadBuffer> source, CompletionListener completion,
			ErrorListener error) {
		return subscribe(name, source, source, completion, error);
	}

	public <K, V> void subscribe(SchemaCodec.Factory factory, ChannelSpec<K, V> spec, SourceListener<K, V> source,
			CompletionListener completion, ErrorListener error) {
		Decoder<V> decoder = factory.decoder(spec);
		subscribe(spec.getName(), source, source.mapPromised(spec::deserializeKey, decoder::decode, error), completion, error);
	}

	public Set<ChannelName> subscribed() {
		return subscriptions.channels();
	}

	public Publisher<Committed<ReadBuffer, ReadBuffer>> subscriber(ChannelName name) {
		return publisher(name);
	}

	public void unsubscribe(ChannelName name, SourceListener<?, ?> listener) {
		subscriptions.unsubscribe(name, listener);
	}

	public Consumer<? super SourceListener<?, ?>> unsubscriber(ChannelName name) {
		Require.nonNull(name);
		return l -> unsubscribe(name, l);
	}
}
