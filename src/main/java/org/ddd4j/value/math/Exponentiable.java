package org.ddd4j.value.math;

import org.ddd4j.value.ValueWrapper;

public interface Exponentiable<T extends Exponentiable<T, O>, O extends Numeric<O>> extends Numeric<T> {

	interface Wrapper<T extends Wrapper<T, V, O>, V extends Exponentiable<V, O>, O extends Numeric<O>> extends
			ValueWrapper<T, V>, Exponentiable<T, O> {

		@Override
		default T divided(T other) {
			return apply(V::divided, other);
		}

		@Override
		default T minus(T other) {
			return apply(V::minus, other);
		}

		@Override
		default T negated() {
			return apply(V::negated);
		}

		@Override
		default T plus(T other) {
			return apply(V::plus, other);
		}

		@Override
		default T power(O other) {
			return applyFunction(V::power, other);
		}

		@Override
		default T times(T other) {
			return apply(V::times, other);
		}
	}

	T power(O other);
}
