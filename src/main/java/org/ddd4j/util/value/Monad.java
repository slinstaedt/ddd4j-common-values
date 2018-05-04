package org.ddd4j.util.value;

import java.util.function.Function;

public interface Monad<T> {

	interface Factory {

		<T> Monad<T> create(T value);
	}

	@SuppressWarnings("unchecked")
	default <X extends Monad<T>> X casted() {
		return (X) this;
	}

	<X> Monad<X> map(Function<? super T, ? extends X> fn);
}
