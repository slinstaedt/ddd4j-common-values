package org.ddd4j.value.versioned;

import java.time.ZonedDateTime;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.value.collection.Props;

public final class Uncommitted<K, V> implements Recorded<K, V> {

	private final K key;
	private final V value;
	private final Revisions expected;
	private final Props header;

	public Uncommitted(K key, V value, Revisions expected) {
		this(key, value, expected, Props.EMTPY);
	}

	public Uncommitted(K key, V value, Revisions expected, Props header) {
		this.key = Require.nonNull(key);
		this.value = Require.nonNull(value);
		this.expected = Require.nonNull(expected);
		this.header = Require.nonNull(header);
	}

	public Committed<K, V> committed(Revision nextExpected, ZonedDateTime timestamp) {
		Revisions expected = this.expected.update(nextExpected);
		Revision actual = expected.revisionOfPartition(nextExpected.getPartition());
		return new Committed<>(key, value, actual, expected, timestamp, header);
	}

	public Conflicting<K, V> conflicts(Revision actual) {
		return new Conflicting<>(key, expected, actual);
	}

	public CommitResult<K, V> resulting(CommitResult<?, ?> result) {
		return result.foldResult(committed -> committed(committed.getActual(), committed.getTimestamp()), conflicting -> conflicts(conflicting.getActual()));
	}

	@Override
	public <X> X foldRecorded(Function<Uncommitted<K, V>, X> uncommitted, Function<Committed<K, V>, X> committed) {
		return uncommitted.apply(this);
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

	@Override
	public V getValue() {
		return value;
	}

	public Uncommitted<K, V> withHeader(Function<Props, Props> headerBuilder) {
		return new Uncommitted<>(key, value, expected, headerBuilder.apply(header));
	}
}