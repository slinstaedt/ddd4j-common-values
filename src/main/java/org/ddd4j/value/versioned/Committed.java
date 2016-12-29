package org.ddd4j.value.versioned;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Function;

import org.ddd4j.aggregate.Identifier;
import org.ddd4j.contract.Require;

public class Committed<E> implements Recorded<E>, CommitResult<E> {

	private final Identifier identifier;
	private final E entry;
	private final Revision actual;
	private final Revision expected;
	private final LocalDateTime timestamp;

	public Committed(Identifier identifier, E entry, Revision actual, Revision expected, LocalDateTime timestamp) {
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
	public <X> X foldRecorded(Function<Uncommitted<E>, X> uncommitted, Function<Committed<E>, X> committed) {
		return committed.apply(this);
	}

	@Override
	public <X> X foldResult(Function<Committed<E>, X> committed, Function<Conflicted<E>, X> conflict) {
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

	public LocalDateTime getTimestamp() {
		return timestamp;
	}
}