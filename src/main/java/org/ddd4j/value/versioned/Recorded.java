package org.ddd4j.value.versioned;

import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Props;

public interface Recorded<K, V> {

	static <K, V> Uncommitted<K, V> uncommitted(K key, V value, Revisions expected) {
		return new Uncommitted<>(key, value, expected, Props.EMTPY);
	}

	static Uncommitted<ReadBuffer, ReadBuffer> uncommitted(ReadBuffer key, ReadBuffer value, Revisions expected) {
		return new Uncommitted<>(key, value, expected, Props.EMTPY);
	}

	<X> X foldRecorded(Function<Uncommitted<K, V>, X> uncommitted, Function<Committed<K, V>, X> committed);

	Props getHeader();

	K getKey();

	V getValue();

	<X, Y> Recorded<X, Y> map(Function<? super K, ? extends X> keyMapper, Function<? super V, ? extends Y> valueMapper);

	int partition(ToIntFunction<? super K> keyHasher);
}