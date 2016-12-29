package org.ddd4j.value.versioned;

import java.util.function.Function;

import org.ddd4j.aggregate.Identifier;
import org.ddd4j.contract.Require;
import org.ddd4j.value.collection.Seq;

public class Conflict<E> implements CommitResult<E> {

	private final Identifier source;
	private final Revision expected;
	private final Revision actual;
	private final Seq<E> entries;

	public Conflict(Identifier source, Revision expected, Revision actual, Seq<E> entries) {
		this.source = Require.nonNull(source);
		this.expected = Require.nonNull(expected);
		this.actual = Require.nonNull(actual);
		this.entries = Require.nonNull(entries);
	}

	@Override
	public <X> X foldResult(Function<Committed<E>, X> committed, Function<Conflict<E>, X> conflict) {
		return conflict.apply(this);
	}
}