package org.ddd4j.value.versioned;

import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.aggregate.Identifier;

public interface CommitResult<E> {

	<X> X foldResult(Function<Committed<E>, X> committed, Function<Conflicting<E>, X> conflict);

	Revision getActual();

	Identifier getIdentifier();

	default CommitResult<E> visitCommitted(Consumer<Committed<E>> committed) {
		return foldResult(c -> {
			committed.accept(c);
			return this;
		}, c -> {
			return this;
		});
	}

	default CommitResult<E> visitCommitted(Runnable committed) {
		return visitCommitted(c -> {
			committed.run();
		});
	}
}