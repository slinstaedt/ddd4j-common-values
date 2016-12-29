package org.ddd4j.value.versioned;

import java.util.function.Function;

import org.ddd4j.aggregate.Identifier;
import org.ddd4j.contract.Require;

public class Conflicted<E> implements CommitResult<E> {

	private final Identifier identifier;
	private final Revision expected;
	private final Revision actual;

	public Conflicted(Identifier identifier, Revision expected, Revision actual) {
		this.identifier = Require.nonNull(identifier);
		this.expected = Require.nonNull(expected);
		this.actual = Require.nonNull(actual);
	}

	@Override
	public <X> X foldResult(Function<Committed<E>, X> committed, Function<Conflicted<E>, X> conflict) {
		return conflict.apply(this);
	}

	@Override
	public Revision getActual() {
		return actual;
	}

	public Revision getExpected() {
		return expected;
	}

	@Override
	public Identifier getIdentifier() {
		return identifier;
	}
}