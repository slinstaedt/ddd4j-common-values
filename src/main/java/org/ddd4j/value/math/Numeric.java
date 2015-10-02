package org.ddd4j.value.math;

import org.ddd4j.value.ValueWrapper;

public interface Numeric<T extends Numeric<T>> extends Invertible<T> {

	interface Wrapper<T extends Wrapper<T, V>, V extends Numeric<V>> extends ValueWrapper<T, V>, Numeric<T> {

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
		default T times(T other) {
			return apply(V::times, other);
		}
	}

	T divided(T other);

	T times(T other);
}
