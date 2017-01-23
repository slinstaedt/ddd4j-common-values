package org.ddd4j.value.versioned;

import java.util.function.Function;

import org.ddd4j.contract.Require;

public final class Conflicting<K, V> implements CommitResult<K, V> {

	private final K key;
	private final Revision expected;
	private final Revision actual;

	public Conflicting(K key, Revision expected, Revision actual) {
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

	public Revision getExpected() {
		return expected;
	}

	@Override
	public K getKey() {
		return key;
	}
}