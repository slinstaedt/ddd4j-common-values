package org.ddd4j.repository.api;

import java.util.Optional;
import java.util.function.Function;

import org.ddd4j.Throwing;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Committed;

public interface Reader<K, V> {

	interface Factory extends Throwing.Closeable {

		Key<Factory> KEY = Key.of(Factory.class);

		Reader<ReadBuffer, ReadBuffer> create(ResourceDescriptor descriptor);
	}

	Promise<Optional<Committed<K, V>>> get(K key);

	default Promise<Optional<V>> getValue(K key) {
		return get(key).thenApply(o -> o.map(Committed::getValue));
	}

	default <X, Y> Reader<X, Y> map(Function<? super X, ? extends K> key, Function<? super V, ? extends Y> value) {
		return k -> get(key.apply(k)).thenApply(o -> o.map(c -> c.mapValue(k, value)));
	}
}