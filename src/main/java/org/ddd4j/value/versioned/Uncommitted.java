package org.ddd4j.value.versioned;

import java.time.ZonedDateTime;
import java.util.function.Function;

import org.ddd4j.contract.Require;

public class Uncommitted<K, V> implements Recorded<K, V> {

	private final K key;
	private final V value;
	private final Revision expected;

	public Uncommitted(K key, V value, Revision expected) {
		this.key = Require.nonNull(key);
		this.value = Require.nonNull(value);
		this.expected = Require.nonNull(expected);
	}

	public Committed<K, V> committed(long nextExpectedOffset, ZonedDateTime timestamp) {
		return committed(expected.next(nextExpectedOffset), timestamp);
	}

	public Committed<K, V> committed(Revision nextExpected, ZonedDateTime timestamp) {
		return new Committed<>(key, value, expected, nextExpected, timestamp);
	}

	public Conflicting<K, V> conflictsWith(Revision actual) {
		return new Conflicting<>(key, expected, actual);
	}

	@Override
	public <X> X foldRecorded(Function<Uncommitted<K, V>, X> uncommitted, Function<Committed<K, V>, X> committed) {
		return uncommitted.apply(this);
	}

	@Override
	public Revision getExpected() {
		return expected;
	}

	@Override
	public K getKey() {
		return key;
	}

	@Override
	public V getValue() {
		return value;
	}
}