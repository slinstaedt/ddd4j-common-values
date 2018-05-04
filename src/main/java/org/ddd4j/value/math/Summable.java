package org.ddd4j.value.math;

import org.ddd4j.util.Self;
import org.ddd4j.value.ValueWrapper;

public interface Summable<T extends Summable<T>> extends Self<T> {

	interface Wrapper<T extends Wrapper<T, V>, V extends Summable<V>> extends ValueWrapper<T, V>, Summable<T> {

		@Override
		default T plus(T other) {
			return apply(V::plus, other);
		}
	}

	T plus(T other);
}
