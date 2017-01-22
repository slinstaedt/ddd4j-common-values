package org.ddd4j.value.versioned;

import java.util.function.Function;

import org.ddd4j.io.ReadBuffer;

public interface Recorded<K, V> {

	static <K, V> Uncommitted<K, V> uncommitted(K key, V value, Revision expected) {
		return new Uncommitted<>(key, value, expected);
	}

	static Uncommitted<ReadBuffer, ReadBuffer> uncommitted(ReadBuffer buffer, Revision expected) {
		return new Uncommitted<>(buffer, buffer, expected);
	}

	<X> X foldRecorded(Function<Uncommitted<K, V>, X> uncommitted, Function<Committed<K, V>, X> committed);

	Revision getExpected();

	K getKey();

	V getValue();
}