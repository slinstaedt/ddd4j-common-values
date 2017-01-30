package org.ddd4j.value.versioned;

import java.time.ZonedDateTime;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.value.collection.Props;

public final class Uncommitted<K, V> implements Recorded<K, V> {

	private final K key;
	private final V value;
	private final Revision expected;
	private final Props header;

	public Uncommitted(K key, V value, Revision expected) {
		this(key, value, expected, Props.EMTPY);
	}

	public Uncommitted(K key, V value, Revision expected, Props header) {
		this.key = Require.nonNull(key);
		this.value = Require.nonNull(value);
		this.expected = Require.nonNull(expected);
		this.header = Require.nonNull(header);
	}

	public Committed<K, V> committed(long nextExpectedOffset, ZonedDateTime timestamp) {
		return committed(expected.next(nextExpectedOffset), timestamp);
	}

	public Committed<K, V> committed(Revision nextExpected, ZonedDateTime timestamp) {
		return new Committed<>(key, value, expected, nextExpected, timestamp, header);
	}

	public Conflicting<K, V> conflicting(Revision actual) {
		return new Conflicting<>(key, expected, actual);
	}

	public CommitResult<K, V> resulting(CommitResult<?, ?> result) {
		return result.foldResult(committed -> committed(committed.getExpected(), committed.getTimestamp()),
				conflicting -> conflicting(conflicting.getActual()));
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
	public Props getHeader() {
		return header;
	}

	@Override
	public K getKey() {
		return key;
	}

	@Override
	public V getValue() {
		return value;
	}

	public Uncommitted<K, V> withHeader(Function<Props, Props> headerBuilder) {
		return new Uncommitted<>(key, value, expected, headerBuilder.apply(header));
	}
}