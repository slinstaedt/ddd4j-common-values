package org.ddd4j.value.versioned;

import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.collection.Props;

public interface Recorded<K, V> {

	static <K, V> Uncommitted<K, V> uncommitted(K key, V value, Revisions expected) {
		return new Uncommitted<>(key, value, expected);
	}

	static Uncommitted<ReadBuffer, ReadBuffer> uncommitted(ReadBuffer buffer, Revisions expected) {
		return new Uncommitted<>(buffer, buffer, expected);
	}

	<X> X foldRecorded(Function<Uncommitted<K, V>, X> uncommitted, Function<Committed<K, V>, X> committed);

	Revisions getExpected();

	Props getHeader();

	K getKey();

	V getValue();

	default int partition(ToIntFunction<? super K> keyHasher) {
		int hash = keyHasher.applyAsInt(getKey());
		return getExpected().partition(hash);
	}
}