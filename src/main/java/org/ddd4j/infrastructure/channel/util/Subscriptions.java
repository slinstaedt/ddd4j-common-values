package org.ddd4j.infrastructure.channel.util;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.repository.RepositoryDefinition;
import org.ddd4j.repository.SchemaCodec;
import org.ddd4j.repository.SchemaCodec.Decoder;
import org.ddd4j.value.Value;
import org.ddd4j.value.versioned.Committed;

public class Subscriptions {

	private static final Listeners NONE = new Listeners(ResourceDescriptor.of("<NONE>"), Promise.failed(new AssertionError()),
			Void.class::getClass);

	private final Function<ResourceDescriptor, Listeners> onSubscribe;
	private final ConcurrentMap<ResourceDescriptor, Listeners> listeners;

	public Subscriptions(Function<ResourceDescriptor, Listeners> onSubscribe) {
		this.onSubscribe = Require.nonNull(onSubscribe);
		this.listeners = new ConcurrentHashMap<>();
	}

	public boolean isEmpty() {
		return listeners.isEmpty();
	}

	public void onNext(ResourceDescriptor resource, Committed<ReadBuffer, ReadBuffer> committed) {
		listeners.getOrDefault(resource, NONE).onNext(committed);
	}

	public Promise<Integer> subscribe(ResourceDescriptor resource, Listener<ReadBuffer, ReadBuffer> listener) {
		return listeners.computeIfAbsent(resource, onSubscribe).add(listener).partitionSize();
	}

	public <K extends Value<K>, V> Promise<Integer> subscribe(RepositoryDefinition<K, V> definition, Listener<K, V> listener,
			SchemaCodec.Factory codecFactory) {
		Decoder<V> decoder = codecFactory.decoder(definition.getValueType());
		Listener<ReadBuffer, ReadBuffer> l = listener.mapPromised(definition::deserializeKey, decoder::decode);
		return listeners.computeIfAbsent(definition.getResource(), onSubscribe).add(listener, l).partitionSize();
	}

	public Set<ResourceDescriptor> resources() {
		return Collections.unmodifiableSet(listeners.keySet());
	}

	public void unsubscribe(ResourceDescriptor resource, Listener<?, ?> listener) {
		listeners.computeIfPresent(resource, (r, s) -> s.remove(listener));
	}
}
