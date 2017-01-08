package org.ddd4j.value.versioned;

import java.util.function.Function;

public interface Recorded<K, V> {

	static <K, V> Uncommitted<K, V> uncommitted(K key, V value, Revision expected) {
		return new Uncommitted<>(key, value, expected);
	}

	<X> X foldRecorded(Function<Uncommitted<K, V>, X> uncommitted, Function<Committed<K, V>, X> committed);

	Revision getExpected();

	K getKey();

	V getValue();
}