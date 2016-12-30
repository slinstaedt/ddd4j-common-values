package org.ddd4j.value.versioned;

import java.time.LocalDateTime;
import java.util.function.Function;

import org.ddd4j.aggregate.Identifier;
import org.ddd4j.contract.Require;

public class Uncommitted<E> implements Recorded<E> {

	private final Identifier identifier;
	private final E entry;
	private final Revision expected;

	public Uncommitted(Identifier identifier, E entry, Revision expected) {
		this.identifier = Require.nonNull(identifier);
		this.entry = Require.nonNull(entry);
		this.expected = Require.nonNull(expected);
	}

	public Committed<E> committed(Revision nextExpected, LocalDateTime timestamp) {
		return new Committed<>(identifier, entry, expected, nextExpected, timestamp);
	}

	public Conflicting<E> conflictsWith(Revision actual) {
		return new Conflicting<>(identifier, expected, actual);
	}

	@Override
	public <X> X foldRecorded(Function<Uncommitted<E>, X> uncommitted, Function<Committed<E>, X> committed) {
		return uncommitted.apply(this);
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
}