package org.ddd4j.value.versioned;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.value.collection.Props;
import org.ddd4j.value.math.Ordered;

public final class Committed<K, V> implements Recorded<K, V>, CommitResult<K, V>, Ordered<Committed<K, V>> {

	private final K key;
	private final V value;
	private final Revision actual;
	private final Revisions expected;
	private final ZonedDateTime timestamp;
	private final Props header;

	public Committed(K key, V value, Revision actual, Revisions expected, ZonedDateTime timestamp, Props header) {
		this.key = Require.nonNull(key);
		this.value = Require.nonNull(value);
		this.actual = Require.nonNull(actual);
		this.expected = Require.nonNull(expected);
		this.timestamp = Require.nonNull(timestamp);
		this.header = Require.nonNull(header);
	}

	public <X> Optional<Committed<K, X>> asOf(Class<X> type) {
		if (type.isInstance(value)) {
			return Optional.of(new Committed<>(key, type.cast(value), actual, expected, timestamp, header));
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
	public Revisions getExpected() {
		return expected;
	}

	@Override
	public Props getHeader() {
		return header;
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