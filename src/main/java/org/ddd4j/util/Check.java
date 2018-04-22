package org.ddd4j.util;

import java.util.function.Predicate;

import org.ddd4j.Require;
import org.ddd4j.Throwing;

@FunctionalInterface
public interface Check<V, T extends V> {

	@FunctionalInterface
	interface Runner<T> {

		void run(T value) throws Exception;

		default boolean runInternal(T value) {
			try {
				run(value);
				return true;
			} catch (Exception e) {
				return Throwing.unchecked(e);
			}
		}
	}

	static <V, T extends V> Check<V, T> is(Class<T> type) {
		Require.nonNull(type);
		return v -> r -> type.isInstance(v) ? r.runInternal(type.cast(v)) : false;
	}

	static <V, T extends V> Check<V, T> isNull() {
		return v -> r -> v == null ? r.runInternal(null) : false;
	}

	Predicate<Runner<T>> value(V value);
}
