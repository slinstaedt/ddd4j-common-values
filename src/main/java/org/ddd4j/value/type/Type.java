package org.ddd4j.value.type;

import org.ddd4j.value.function.Curry;

@FunctionalInterface
public interface Type<C extends Curry<?, ?>> extends Curry<Type<C>, C> {

	default C constructor() {
		return bind(this);
	}

	default boolean isInstance(Instance candidate) {
		return candidate.instanceOf(this);
	}
}
