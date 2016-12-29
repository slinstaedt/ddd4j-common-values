package org.ddd4j.value.versioned;

import java.util.function.Function;

import org.ddd4j.aggregate.Identifier;

public interface Recorded<E> {

	static <E> Uncommitted<E> uncommitted(Identifier source, E entry, Revision expected) {
		return new Uncommitted<>(source, entry, expected);
	}

	<X> X foldRecorded(Function<Uncommitted<E>, X> uncommitted, Function<Committed<E>, X> committed);

	E getEntry();

	Revision getExpected();

	Identifier getIdentifier();
}