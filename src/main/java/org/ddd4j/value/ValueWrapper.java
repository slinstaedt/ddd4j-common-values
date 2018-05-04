package org.ddd4j.value;

import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

import org.ddd4j.util.Self;

public interface ValueWrapper<T extends ValueWrapper<T, V>, V> extends Self<T> {

	default T apply(BinaryOperator<V> operator, T other) {
		return wrap(operator.apply(value(), other.value()));
	}

	default T apply(UnaryOperator<V> operator) {
		return wrap(operator.apply(value()));
	}

	default <O> T applyFunction(BiFunction<V, O, V> operator, O other) {
		return wrap(operator.apply(value(), other));
	}

	V value();

	T wrap(V value);
}
