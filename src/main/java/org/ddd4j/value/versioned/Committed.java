package org.ddd4j.value.versioned;

import java.time.Instant;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import org.ddd4j.infrastructure.domain.header.Headers;
import org.ddd4j.util.Require;
import org.ddd4j.value.math.Ordered;

//TODO add actual date beside transaction date
public class Committed<K, V> implements Recorded<K, V>, CommitResult<K, V>, Ordered<Committed<K, V>> {

	public static class Published<K, V> extends Committed<K, V> {

		Published(K key, V value, Revision actual, Revision next, Instant timestamp, Headers headers) {
			super(key, value, actual, next, timestamp, headers);
		}

		@Override
		public <X, Y> Published<X, Y> map(Function<? super K, ? extends X> keyMapper, Function<? super V, ? extends Y> valueMapper) {
			return new Published<>(keyMapper.apply(super.key), valueMapper.apply(super.value), super.actual, super.nextExpected,
					super.timestamp, super.headers);
		}

		@Override
		public <X> X onCommitted(Function<Committed<K, V>, ? extends X> committed, X ignore) {
			return ignore;
		}

		@Override
		public <X, Y> Published<X, Y> withKeyValueFrom(Recorded<X, Y> recorded) {
			return map(r -> recorded.getKey(), v -> recorded.getValue());
		}
	}

	private final K key;
	private final V value;
	private final Revision actual;
	private final Revision nextExpected;
	private final Instant timestamp;
	private final Headers headers;

	public Committed(K key, V value, Revision actual, Revision nextExpected, Instant timestamp, Headers headers) {
		this.key = Require.nonNull(key);
		this.value = Require.nonNull(value);
		this.actual = Require.nonNull(actual);
		this.nextExpected = Require.nonNull(nextExpected);
		this.timestamp = Require.nonNull(timestamp);
		this.headers = Require.nonNull(headers);
	}

	@Override
	public Committed<K, V> committed(Revision nextExpected) {
		return this;
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
	public <X> X foldResult(Function<Committed<K, V>, X> committed, Function<Conflicting<K, V>, X> conflict) {
		return committed.apply(this);
	}

	@Override
	public Revision getActual() {
		return actual;
	}

	@Override
	public Headers getHeaders() {
		return headers;
	}

	@Override
	public K getKey() {
		return key;
	}

	public Revision getNextExpected() {
		return nextExpected;
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
	public <X, Y> Committed<X, Y> map(Function<? super K, ? extends X> keyMapper, Function<? super V, ? extends Y> valueMapper) {
		return new Committed<>(keyMapper.apply(key), valueMapper.apply(value), actual, nextExpected, timestamp, headers);
	}

	@Override
	public <X, Y> Committed<X, Y> mapKey(Function<? super K, ? extends X> keyMapper, Y value) {
		return map(keyMapper, v -> value);
	}

	@Override
	public <X, Y> Committed<X, Y> mapValue(X key, Function<? super V, ? extends Y> valueMapper) {
		return map(k -> key, valueMapper);
	}

	@Override
	public <X> X onCommitted(Function<Committed<K, V>, ? extends X> committed, X ignore) {
		return committed.apply(this);
	}

	@Override
	public int partition(ToIntFunction<? super K> keyHasher) {
		return actual.getPartition();
	}

	public Position position(IntFunction<Revision> state) {
		Revision expected = state.apply(actual.getPartition());
		return expected.comparePosition(actual);
	}

	public Published<K, V> published() {
		return new Published<>(key, value, actual, nextExpected, timestamp, headers);
	}

	@Override
	public <X, Y> Committed<X, Y> withKeyValue(X key, Y value) {
		return map(k -> key, v -> value);
	}
}