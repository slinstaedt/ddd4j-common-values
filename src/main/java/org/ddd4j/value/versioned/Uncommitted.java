package org.ddd4j.value.versioned;

import java.time.ZonedDateTime;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.ddd4j.Require;
import org.ddd4j.collection.Props;

//TODO rename to Attempt?
public final class Uncommitted<K, V> implements Recorded<K, V> {

	private final K key;
	private final V value;
	private final Revisions actualExpected;
	private final Props header;

	public Uncommitted(K key, V value, Revisions expected) {
		this(key, value, expected, Props.EMTPY);
	}

	public Uncommitted(K key, V value, Revisions actualExpected, Props header) {
		this.key = Require.nonNull(key);
		this.value = Require.nonNull(value);
		this.actualExpected = Require.nonNull(actualExpected);
		this.header = Require.nonNull(header);
	}

	public Committed<K, V> committed(Revision nextExpected, ZonedDateTime timestamp) {
		Revision actual = actualExpected.revisionOfPartition(nextExpected.getPartition());
		return new Committed<>(key, value, actual, nextExpected, timestamp, header);
	}

	public Conflicting<K, V> conflicts(Revision actual) {
		return new Conflicting<>(key, actualExpected, actual);
	}

	public CommitResult<K, V> resulting(CommitResult<?, ?> result) {
		return result.foldResult(committed -> committed(committed.getActual(), committed.getTimestamp()),
				conflicting -> conflicts(conflicting.getActual()));
	}

	@Override
	public <X> X foldRecorded(Function<Uncommitted<K, V>, X> uncommitted, Function<Committed<K, V>, X> committed) {
		return uncommitted.apply(this);
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

	@Override
	public <X, Y> Uncommitted<X, Y> map(Function<? super K, ? extends X> keyMapper, Function<? super V, ? extends Y> valueMapper) {
		return new Uncommitted<>(keyMapper.apply(key), valueMapper.apply(value), actualExpected, header);
	}

	@Override
	public int partition(ToIntFunction<? super K> keyHasher) {
		int hash = keyHasher.applyAsInt(getKey());
		return actualExpected.partition(hash);
	}

	public Uncommitted<K, V> withHeader(Function<Props, Props> headerBuilder) {
		return new Uncommitted<>(key, value, actualExpected, headerBuilder.apply(header));
	}
}