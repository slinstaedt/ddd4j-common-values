package org.ddd4j.value.type;

@FunctionalInterface
public interface Instance {

	Type<?> getType();

	default boolean instanceOf(Type<?> type) {
		return getType().equals(type);
	}
}
