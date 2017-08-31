package org.ddd4j.value.versioned;

import java.util.function.Consumer;
import java.util.function.Function;

public interface CommitResult<K, V> {

	<X> X foldResult(Function<Committed<K, V>, X> committed, Function<Conflicting<K, V>, X> conflicting);

	Revision getActual();

	K getKey();

	CommitResult<K, V> onCommitted(Consumer<? super Committed<K, V>> committed);

	<X, Y> CommitResult<X, Y> withValuesFrom(Recorded<X, Y> recorded);
}