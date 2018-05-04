package org.ddd4j.value.versioned;

import java.time.Instant;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import org.ddd4j.infrastructure.domain.header.Headers;
import org.ddd4j.infrastructure.domain.header.HeaderKey;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Require;

//TODO rename to Attempt?
public class Uncommitted<K, V> implements Recorded<K, V> {

	private final K key;
	private final V value;
	private final Headers headers;
	private final Instant timestamp;
	private final IntFunction<Revision> revisionOfPartition;

	Uncommitted(K key, V value, Headers headers, Instant timestamp, IntFunction<Revision> revisionOfPartition) {
		this.key = Require.nonNull(key);
		this.value = Require.nonNull(value);
		this.headers = Require.nonNull(headers);
		this.timestamp = Require.nonNull(timestamp);
		this.revisionOfPartition = Require.nonNull(revisionOfPartition);
	}

	@Override
	public Committed<K, V> committed(Revision nextExpected) {
		Revision actual = revisionOfPartition.apply(nextExpected.getPartition());
		return new Committed<>(key, value, actual, nextExpected, timestamp, headers);
	}

	public Conflicting<K, V> conflicts(Revision actual) {
		return new Conflicting<>(key, revisionOfPartition.apply(actual.getPartition()), actual);
	}

	@Override
	public Headers getHeaders() {
		return headers;
	}

	@Override
	public K getKey() {
		return key;
	}

	@Override
	public Instant getTimestamp() {
		return timestamp;
	}

	@Override
	public V getValue() {
		return value;
	}

	@Override
	public <X, Y> Uncommitted<X, Y> map(Function<? super K, ? extends X> keyMapper, Function<? super V, ? extends Y> valueMapper) {
		return new Uncommitted<>(keyMapper.apply(key), valueMapper.apply(value), headers, timestamp, revisionOfPartition);
	}

	@Override
	public <X, Y> Uncommitted<X, Y> mapKey(Function<? super K, ? extends X> keyMapper, Y value) {
		return map(keyMapper, v -> value);
	}

	@Override
	public <X, Y> Uncommitted<X, Y> mapValue(X key, Function<? super V, ? extends Y> valueMapper) {
		return map(k -> key, valueMapper);
	}

	@Override
	public int partition(ToIntFunction<? super K> partitioner) {
		int partition = partitioner.applyAsInt(key);
		revisionOfPartition.apply(partition);
		return partition;
	}

	public <X> Uncommitted<K, V> withHeader(HeaderKey<X> headerKey, X headerValue, WriteBuffer buffer) {
		return new Uncommitted<>(key, value, headers.with(headerKey, headerValue, buffer), timestamp, revisionOfPartition);
	}

	public Uncommitted<K, V> withHeader(String headerKey, ReadBuffer headerValue) {
		return new Uncommitted<>(key, value, headers.with(headerKey, headerValue), timestamp, revisionOfPartition);
	}
}