package org.ddd4j.value;

import org.ddd4j.value.Type.Variable;

public interface Typed<T> {

	default Type<T> getType() {
		Variable<Typed<T>, T> var = Type.variable(Typed.class, 0, Object.class);
		return Type.ofInstance(this).resolve(var);
	}
}
