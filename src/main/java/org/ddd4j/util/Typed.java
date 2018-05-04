package org.ddd4j.util;

import org.ddd4j.util.Type.Variable;

public interface Typed<T> {

	static <D, T> Type<T> resolve(D self, Class<? super D> declaration, int variableIndex) {
		Variable<? super D, T> var = Type.variable(declaration, variableIndex, Object.class);
		return Type.ofInstance(self).resolve(var);
	}

	default Type<T> getType() {
		return resolve(this, Typed.class, 0);
	}
}
