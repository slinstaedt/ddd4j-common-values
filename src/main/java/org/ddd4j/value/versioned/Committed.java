package org.ddd4j.value.versioned;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import org.ddd4j.Require;
import org.ddd4j.util.Props;
import org.ddd4j.value.math.Ordered;

//TODO add actual date beside transaction date
public class Committed<K, V> implements Recorded<K, V>, CommitResult<K, V>, Ordered<Committed<K, V>> {

	public static class Published<K, V> extends Committed<K, V> {

		Published(K key, V value, Revision actual, Revision next, Instant timestamp, Props header) {
			super(key, value, actual, next, timestamp, header);
		}

		@Override
		public CommitResult<K, V> onCommitted(Consumer<? super Committed<K, V>> committed) {
			return this;
		}
	}

	private final K key;
	private final V value;
	private final Revision actual;
	private final Revision nextExpected;
	private final Instant timestamp;
	private final Props header;

	public Committed(K key, V value, Revision actual, Revision nextExpected, Instant timestamp, Props header) {
		this.key = Require.nonNull(key);
		this.value = Require.nonNull(value);
		this.actual = Require.nonNull(actual);
		this.nextExpected = Require.nonNull(nextExpected);
		this.timestamp = Require.nonNull(timestamp);
		this.header = Require.nonNull(header);
	}

	public <X> Optional<Committed<K, X>> asOf(Class<X> type) {
		if (type.isInstance(value)) {
			return Optional.of(new Committed<>(key, type.cast(value), actual, nextExpected, timestamp, header));
		} else {
			return Optional.empty();
		}
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
	public Props getHeader() {
		return header;
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
		return new Committed<>(keyMapper.apply(key), valueMapper.apply(value), actual, nextExpected, timestamp, header);
	}

	public <X, Y> Committed<X, Y> mapKey(Function<? super K, ? extends X> keyMapper, Y value) {
		return new Committed<>(keyMapper.apply(key), value, actual, nextExpected, timestamp, header);
	}

	public <X, Y> Committed<X, Y> mapValue(X key, Function<? super V, ? extends Y> valueMapper) {
		return new Committed<>(key, valueMapper.apply(value), actual, nextExpected, timestamp, header);
	}

	@Override
	public CommitResult<K, V> onCommitted(Consumer<? super Committed<K, V>> committed) {
		committed.accept(this);
		return this;
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
		return new Published<>(key, value, actual, nextExpected, timestamp, header);
	}

	@Override
	public <X, Y> Committed<X, Y> with(Function<? super K, ? extends X> keyMapper, Y value) {
		return new Committed<>(keyMapper.apply(key), value, actual, nextExpected, timestamp, header);
	}

	@Override
	public <X, Y> Committed<X, Y> withValuesFrom(Recorded<X, Y> recorded) {
		return new Committed<>(recorded.getKey(), recorded.getValue(), actual, nextExpected, timestamp, header);
	}
}