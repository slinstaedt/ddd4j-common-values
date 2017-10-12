package org.ddd4j.infrastructure.channel.spi;

import java.util.Optional;
import java.util.function.Function;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Committed;

public interface Reader<K, V> {

	interface Factory extends DataAccessFactory {

		Reader<ReadBuffer, ReadBuffer> create(ChannelName resource);
	}

	Key<Factory> FACTORY = Key.of(Factory.class);

	Promise<Optional<Committed<K, V>>> get(K key);

	default Promise<Optional<V>> getValue(K key) {
		return get(key).thenApply(o -> o.map(Committed::getValue));
	}

	default Promise<V> getValueFailOnMissing(K key) {
		return getValue(key).checkOrFail(Optional::isPresent).thenApply(Optional::get);
	}

	default <X, Y> Reader<X, Y> map(Function<? super X, ? extends K> key, Function<? super V, ? extends Y> value) {
		return k -> get(key.apply(k)).thenApply(o -> o.map(c -> c.mapValue(k, value)));
	}
}