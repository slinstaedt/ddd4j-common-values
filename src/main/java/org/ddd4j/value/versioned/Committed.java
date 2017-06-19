package org.ddd4j.value.versioned;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.ddd4j.Require;
import org.ddd4j.value.collection.Props;
import org.ddd4j.value.math.Ordered;

public class Committed<K, V> implements Recorded<K, V>, CommitResult<K, V>, Ordered<Committed<K, V>> {

	public static class Published<K, V> extends Committed<K, V> {

		Published(K key, V value, Revision actual, Revision next, ZonedDateTime timestamp, Props header) {
			super(key, value, actual, next, timestamp, header);
		}

		@Override
		public CommitResult<K, V> onCommitted(Consumer<Committed<K, V>> committed) {
			return this;
		}

		@Override
		public CommitResult<K, V> onCommitted(Runnable committed) {
			return this;
		}
	}

	private final K key;
	private final V value;
	private final Revision actual;
	private final Revision nextExpected;
	private final ZonedDateTime timestamp;
	private final Props header;

	Committed(K key, V value, Revision actual, Revision next, ZonedDateTime timestamp, Props header) {
		this.key = Require.nonNull(key);
		this.value = Require.nonNull(value);
		this.actual = Require.nonNull(actual);
		this.nextExpected = Require.nonNull(next);
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

	public Revision getNextExpected() {
		return nextExpected;
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

	@Override
	public int partition(ToIntFunction<? super K> keyHasher) {
		return actual.getPartition();
	}

	public Published<K, V> published() {
		return new Published<>(key, value, actual, nextExpected, timestamp, header);
	}
}