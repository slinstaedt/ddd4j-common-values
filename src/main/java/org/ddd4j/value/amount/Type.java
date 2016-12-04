package org.ddd4j.value.amount;

import java.util.function.UnaryOperator;

import org.ddd4j.value.Self;

public interface Type<T extends Type<T, Q>, Q> extends Self<T> {

	default <X extends Q> Amount<X, T> of(X quantity) {
		return new Amount<>(quantity, self());
	}

	default T to(UnaryOperator<? super Q> operator) {
	}
}
