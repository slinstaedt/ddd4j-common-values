package org.ddd4j.value.versioned;

import java.time.Instant;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Props;

public interface Recorded<K, V> {

	static <K, V> Uncommitted<K, V> uncommitted(K key, V value, Revisions expected, Instant timestamp) {
		return new Uncommitted<>(key, value, expected, timestamp, Props.EMTPY);
	}

	static Uncommitted<ReadBuffer, ReadBuffer> uncommitted(ReadBuffer key, ReadBuffer value, Revisions expected, Instant timestamp) {
		return new Uncommitted<>(key, value, expected, timestamp, Props.EMTPY);
	}

	Committed<K, V> committed(Revision nextExpected);

	Props getHeader();

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

	int partition(ToIntFunction<? super K> keyHasher);
}