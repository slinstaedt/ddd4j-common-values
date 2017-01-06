package org.ddd4j.value.versioned;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Function;

import org.ddd4j.aggregate.Identifier;
import org.ddd4j.contract.Require;
import org.ddd4j.value.math.Ordered;

public class Committed<E> implements Recorded<E>, CommitResult<E>, Ordered<Committed<E>> {

	private final Identifier identifier;
	private final E entry;
	private final Revision actual;
	private final Revision expected;
	private final ZonedDateTime timestamp;

	public Committed(Identifier identifier, E entry, Revision actual, Revision expected, ZonedDateTime timestamp) {
		this.identifier = Require.nonNull(identifier);
		this.entry = Require.nonNull(entry);
		this.actual = Require.nonNull(actual);
		this.expected = Require.nonNull(expected);
		this.timestamp = Require.nonNull(timestamp);
	}

	public <X> Optional<Committed<X>> asOf(Class<X> type) {
		if (type.isInstance(entry)) {
			return Optional.of(new Committed<>(identifier, type.cast(entry), actual, expected, timestamp));
		} else {
			return Optional.empty();
		}
	}

	@Override
	public int compareTo(Committed<E> other) {
		if (this.actual.equals(other.actual)) {
			return Long.compareUnsigned(this.actual.getOffset(), other.actual.getOffset());
		} else {
			return this.timestamp.compareTo(other.timestamp);
		}
	}

	@Override
	public <X> X foldRecorded(Function<Uncommitted<E>, X> uncommitted, Function<Committed<E>, X> committed) {
		return committed.apply(this);
	}

	@Override
	public <X> X foldResult(Function<Committed<E>, X> committed, Function<Conflicting<E>, X> conflict) {
		return committed.apply(this);
	}

	@Override
	public Revision getActual() {
		return actual;
	}

	@Override
	public E getEntry() {
		return entry;
	}

	@Override
	public Revision getExpected() {
		return expected;
	}

	@Override
	public Identifier getIdentifier() {
		return identifier;
	}

	public ZonedDateTime getTimestamp() {
		return timestamp;
	}
}