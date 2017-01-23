package org.ddd4j.value.versioned;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.value.math.Ordered;

public final class Committed<K, V> implements Recorded<K, V>, CommitResult<K, V>, Ordered<Committed<K, V>> {

	private final K key;
	private final V value;
	private final Revision actual;
	private final Revision expected;
	private final ZonedDateTime timestamp;

	public Committed(K key, V value, Revision actual, Revision expected, ZonedDateTime timestamp) {
		this.key = Require.nonNull(key);
		this.value = Require.nonNull(value);
		this.actual = Require.nonNull(actual);
		this.expected = Require.nonNull(expected);
		this.timestamp = Require.nonNull(timestamp);
	}

	public <X> Optional<Committed<K, X>> asOf(Class<X> type) {
		if (type.isInstance(value)) {
			return Optional.of(new Committed<>(key, type.cast(value), actual, expected, timestamp));
		} else {
			return Optional.empty();
		}
	}

	@Override
	public int compareTo(Committed<K, V> other) {
		if (this.actual.equals(other.actual)) {
			return Long.compareUnsigned(this.actual.getOffset(), other.actual.getOffset());
		} else {
			return this.timestamp.compareTo(other.timestamp);
		}
	}

	@Override
	public <X> X foldRecorded(Function<Uncommitted<K, V>, X> uncommitted, Function<Committed<K, V>, X> committed) {
		return committed.apply(this);
	}

	@Override
	public <X> X foldResult(Function<Committed<K, V>, X> committed, Function<Conflicting<K, V>, X> conflict) {
		return committed.apply(this);
	}

	@Override
	public Revision getActual() {
		return actual;
	}

	@Override
	public Revision getExpected() {
		return expected;
	}

	@Override
	public K getKey() {
		return key;
	}

	public ZonedDateTime getTimestamp() {
		return timestamp;
	}

	@Override
	public V getValue() {
		return value;
	}
}