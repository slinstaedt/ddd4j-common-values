package org.ddd4j.value.versioned;

import java.util.function.Consumer;
import java.util.function.Function;

public interface CommitResult<K, V> {

	<X> X foldResult(Function<Committed<K, V>, X> committed, Function<Conflicting<K, V>, X> conflict);

	Revision getActual();

	K getKey();

	default CommitResult<K, V> visitCommitted(Consumer<Committed<K, V>> committed) {
		return foldResult(c -> {
			committed.accept(c);
			return this;
		}, c -> {
			return this;
		});
	}

	default CommitResult<K, V> visitCommitted(Runnable committed) {
		return visitCommitted(c -> {
			committed.run();
		});
	}
}