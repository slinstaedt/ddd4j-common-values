package org.ddd4j.util;

/**
 * Marker interface for self types.
 *
 * @param <S>
 *            The type itself
 */
public interface Self<S extends Self<S>> {

	@SuppressWarnings("unchecked")
	default S self() {
		return (S) this;
	}
}
