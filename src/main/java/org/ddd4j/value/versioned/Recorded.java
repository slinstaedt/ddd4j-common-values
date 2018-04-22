package org.ddd4j.value.versioned;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.domain.header.HeaderKey;
import org.ddd4j.infrastructure.domain.header.Headers;

public interface Recorded<K, V> {

	static <K, V> Uncommitted<K, V> uncommitted(K key, V value, Headers headers, Instant timestamp, Revision expected) {
		return new Uncommitted<>(key, value, headers, timestamp, Require.nonNull(expected)::checkPartition);
	}

	static <K, V> Uncommitted<K, V> uncommitted(K key, V value, Headers headers, Instant timestamp, Revisions expected) {
		return new Uncommitted<>(key, value, headers, timestamp, Require.nonNull(expected)::revisionOfPartition);
	}

	Committed<K, V> committed(Revision nextExpected);

	default <X> Optional<X> getHeader(HeaderKey<X> key) {
		return getHeaders().get(key);
	}

	Headers getHeaders();

	K getKey();

	Instant getTimestamp();

	V getValue();

	<X, Y> Recorded<X, Y> map(Function<? super K, ? extends X> keyMapper, Function<? super V, ? extends Y> valueMapper);

	default <X, Y> Recorded<X, Y> mapKey(Function<? super K, ? extends X> keyMapper, Y value) {
		return map(keyMapper, v -> value);
	}

	default <X, Y> Recorded<X, Y> mapValue(X key, Function<? super V, ? extends Y> valueMapper) {
		return map(k -> key, valueMapper);
	}

	int partition(ToIntFunction<? super K> partitioner);
}