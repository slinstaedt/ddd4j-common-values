package org.ddd4j.value;

public interface Self<S extends Self<S>> {

	@SuppressWarnings("unchecked")
	default S self() {
		return (S) this;
	}
}
