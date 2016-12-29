package org.ddd4j.value.versioned;

import java.time.LocalDateTime;
import java.util.function.Function;

import org.ddd4j.aggregate.Identifier;
import org.ddd4j.contract.Require;
import org.ddd4j.value.collection.Seq;

public class Uncommitted<E> implements Recorded<E> {

	private final Identifier identifier;
	private final Seq<E> changes;
	private final Revision expected;

	public Uncommitted(Identifier identifier, Seq<E> changes, Revision expected) {
		this.identifier = Require.nonNull(identifier);
		this.changes = Require.nonNull(changes);
		this.expected = Require.nonNull(expected);
	}

	public Seq<Committed<E>> committed(Revision nextExpected, LocalDateTime timestamp) {
		return new Committed<>(identifier, entry, expected, nextExpected, timestamp);
	}

	public Conflict<E> conflictsWith(Revision actual, Seq<E> entries) {
		return new Conflict<>(identifier, expected, actual, entries);
	}

	@Override
	public <X> X foldRecorded(Function<Uncommitted<E>, X> uncommitted, Function<Committed<E>, X> committed) {
		return uncommitted.apply(this);
	}

	public Seq<E> getChanges() {
		return changes;
	}

	public Revision getExpected() {
		return expected;
	}

	@Override
	public Identifier getIdentifier() {
		return identifier;
	}
}