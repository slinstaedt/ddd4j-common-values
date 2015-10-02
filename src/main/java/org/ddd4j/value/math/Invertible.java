package org.ddd4j.value.math;

import static java.util.Objects.requireNonNull;

import org.ddd4j.value.ValueWrapper;

public interface Invertible<T extends Invertible<T>> extends Summable<T> {

	enum Sign {
		POSITIVE {

			@Override
			<V extends Invertible<V>> V apply(V value) {
				return requireNonNull(value);
			}
		},
		NEGATIVE {

			@Override
			<V extends Invertible<V>> V apply(V value) {
				return value.negated();
			}
		},
		ZERO {

			@Override
			<V extends Invertible<V>> V apply(V value) {
				// TODO
				return null;
			}
		};

		public static Sign of(int value) {
			if (value > 0) {
				return POSITIVE;
			} else if (value < 0) {
				return NEGATIVE;
			} else {
				return ZERO;
			}
		}

		abstract <V extends Invertible<V>> V apply(V value);
	}

	interface Wrapper<T extends Wrapper<T, V>, V extends Invertible<V>> extends ValueWrapper<T, V>, Invertible<T> {

		@Override
		public default T minus(T other) {
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
		default Sign sign() {
			return value().sign();
		}
	}

	default T minus(T other) {
		return plus(other.negated());
	}

	T negated();

	Sign sign();
}
