package org.ddd4j.value.versioned;

import java.util.function.Function;

public interface CommitResult<K, V> {

	<X> X foldResult(Function<Committed<K, V>, X> committed, Function<Conflicting<K, V>, X> conflicting);

	Revision getActual();

	K getKey();

	<X> X onCommitted(Function<Committed<K, V>, ? extends X> committed, X ignore);

	<X, Y> CommitResult<X, Y> withKeyValue(X key, Y value);

	default <X, Y> CommitResult<X, Y> withKeyValueFrom(Recorded<X, Y> recorded) {
		return withKeyValue(recorded.getKey(), recorded.getValue());
	}
}