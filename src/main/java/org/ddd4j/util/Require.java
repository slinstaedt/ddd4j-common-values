package org.ddd4j.util;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

@FunctionalInterface
public interface Require<T> extends Function<Object, T> {

	Require<Object> NOTHING = x -> x;

	static String nonEmpty(String text) {
		nonNull(text != null);
		that(!text.isEmpty());
		return text;
	}

	static <T extends Iterable<?>> T nonEmpty(T collection) {
		nonNull(collection);
		if (collection instanceof Collection) {
			that(!((Collection<?>) collection).isEmpty());
		} else {
			that(collection.iterator().hasNext());
		}
		return collection;
	}

	static <T> T nonNull(T object) {
		return nonNull(object, null);
	}

	static <T> T nonNull(T object, String message) {
		if (object == null) {
			throw message != null ? new AssertionError(message) : new AssertionError();
		}
		return object;
	}

	// TODO rename to "nonNulls"
	static void nonNulls(Object o) {
		nonNull(o);
	}

	static void nonNulls(Object o1, Object o2) {
		nonNull(o1);
		nonNull(o2);
	}

	static void nonNulls(Object o1, Object o2, Object o3) {
		nonNull(o1);
		nonNull(o2);
		nonNull(o3);
	}

	static void nonNulls(Object o1, Object o2, Object o3, Object o4) {
		nonNull(o1);
		nonNull(o2);
		nonNull(o3);
		nonNull(o4);
	}

	static void nonNulls(Object o1, Object o2, Object o3, Object o4, Object o5) {
		nonNull(o1);
		nonNull(o2);
		nonNull(o3);
		nonNull(o4);
		nonNull(o5);
	}

	@SafeVarargs
	static <T> T[] nonNulls(T... objects) {
		nonNull(objects);
		for (int i = 0; i < objects.length; i++) {
			nonNull(objects[i], i + ". parameter is NULL");
		}
		return objects;
	}

	static <T extends Iterable<?>> T nonNullElements(T iterable) {
		nonNull(iterable);
		iterable.forEach(Require::nonNull);
		return iterable;
	}

	static Require<Object> require() {
		return NOTHING;
	}

	static void that(boolean condition) {
		if (!condition) {
			throw new AssertionError();
		}
	}

	static <T> T that(T object, boolean condition) {
		that(condition);
		return object;
	}

	static <T> T that(T object, Predicate<? super T> predicate) {
		that(predicate.test(object));
		return object;
	}

	default <X extends T> Require<X> is(Class<X> type) {
		nonNull(type);
		return o -> type.cast(that(type::isInstance).apply(o));
	}

	default Require<T> nonNull() {
		return that(Objects::nonNull);
	}

	default <X extends T> X test(X object) {
		apply(object);
		return object;
	}

	default Require<T> that(Predicate<? super T> predicate) {
		nonNull(predicate);
		return o -> Require.that(apply(o), t -> t == null || predicate.test(t));
	}

	default Require<T> thatNot(Predicate<? super T> predicate) {
		return that(predicate.negate());
	}
}
