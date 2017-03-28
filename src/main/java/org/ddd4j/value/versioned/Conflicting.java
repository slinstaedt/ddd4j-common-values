package org.ddd4j.value.versioned;

import java.util.function.Function;

import org.ddd4j.Require;

public final class Conflicting<K, V> implements CommitResult<K, V> {

	private final K key;
	private final Revisions expected;
	private final Revision actual;

	public Conflicting(K key, Revisions expected, Revision actual) {
		this.key = Require.nonNull(key);
		this.expected = Require.nonNull(expected);
		this.actual = Require.nonNull(actual);
	}

	@Override
	public <X> X foldResult(Function<Committed<K, V>, X> committed, Function<Conflicting<K, V>, X> conflict) {
		return conflict.apply(this);
	}

	@Override
	public Revision getActual() {
		return actual;
	}

	public Revisions getExpected() {
		return expected;
	}

	@Override
	public K getKey() {
		return key;
	}
}