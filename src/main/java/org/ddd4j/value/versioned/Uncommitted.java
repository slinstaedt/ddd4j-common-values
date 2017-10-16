package org.ddd4j.value.versioned;

import java.time.OffsetDateTime;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.ddd4j.Require;
import org.ddd4j.util.Props;

//TODO rename to Attempt?
public final class Uncommitted<K, V> implements Recorded<K, V> {

	private final K key;
	private final V value;
	private final Revisions expected;
	private final Props header;

	public Uncommitted(K key, V value, Revisions expected, Props header) {
		this.key = Require.nonNull(key);
		this.value = Require.nonNull(value);
		this.expected = Require.nonNull(expected);
		this.header = Require.nonNull(header);
	}

	@Override
	public Committed<K, V> committed(Revision nextExpected, OffsetDateTime timestamp) {
		Revision actual = expected.revisionOfPartition(nextExpected.getPartition());
		return new Committed<>(key, value, actual, nextExpected, timestamp, header);
	}

	public Conflicting<K, V> conflicts(Revision actual) {
		return new Conflicting<>(key, expected, actual);
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
		return new Uncommitted<>(keyMapper.apply(key), valueMapper.apply(value), expected, header);
	}

	@Override
	public int partition(ToIntFunction<? super K> keyHasher) {
		int hash = keyHasher.applyAsInt(key);
		return expected.partition(hash);
	}

	@Override
	public <X, Y> Uncommitted<X, Y> with(Function<? super K, ? extends X> keyMapper, Y value) {
		return new Uncommitted<>(keyMapper.apply(key), value, expected, header);
	}

	public Uncommitted<K, V> withHeader(Function<Props, Props> headerBuilder) {
		return new Uncommitted<>(key, value, expected, headerBuilder.apply(header));
	}
}