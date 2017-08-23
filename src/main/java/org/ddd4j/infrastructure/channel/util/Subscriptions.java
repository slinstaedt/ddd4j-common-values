package org.ddd4j.infrastructure.channel.util;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.infrastructure.channel.domain.ChannelSpec;
import org.ddd4j.infrastructure.channel.util.SchemaCodec.Decoder;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;

public class Subscriptions {

	private static final Listeners NONE = new Listeners(ChannelName.of("<NONE>"), Promise.failed(new AssertionError()),
			Void.class::getClass);

	private final Function<ChannelName, Listeners> onSubscribe;
	private final ConcurrentMap<ChannelName, Listeners> listeners;

	public Subscriptions(Function<ChannelName, Listeners> onSubscribe) {
		this.onSubscribe = Require.nonNull(onSubscribe);
		this.listeners = new ConcurrentHashMap<>();
	}

	public boolean isEmpty() {
		return listeners.isEmpty();
	}

	public void onNext(ChannelName resource, Committed<ReadBuffer, ReadBuffer> committed) {
		listeners.getOrDefault(resource, NONE).onNext(committed);
	}

	public Promise<Integer> subscribe(ChannelName resource, SourceListener<ReadBuffer, ReadBuffer> listener) {
		return listeners.computeIfAbsent(resource, onSubscribe).add(listener).partitionSize();
	}

	public <K, V> Promise<Integer> subscribe(ChannelSpec<K, V> spec, SourceListener<K, V> listener, SchemaCodec.Factory codecFactory) {
		Decoder<V> decoder = codecFactory.decoder(spec);
		SourceListener<ReadBuffer, ReadBuffer> sl = listener.mapPromised(spec::deserializeKey, decoder::decode);
		return listeners.computeIfAbsent(spec.getName(), onSubscribe).add(listener, sl).partitionSize();
	}

	public Set<ChannelName> resources() {
		return Collections.unmodifiableSet(listeners.keySet());
	}

	public void unsubscribe(ChannelName resource, SourceListener<?, ?> listener) {
		listeners.computeIfPresent(resource, (r, s) -> s.remove(listener));
	}
}
