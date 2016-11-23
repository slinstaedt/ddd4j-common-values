package org.ddd4j.contract;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

@FunctionalInterface
public interface Require<T> extends Function<Object, T> {

	Require<Object> NOTHING = x -> x;

	static String nonEmpty(String text) {
		assert text != null;
		assert !text.isEmpty();
		return text;
	}

	static <T extends Iterable<?>> T nonEmpty(T collection) {
		assert collection != null;
		if (collection instanceof Collection) {
			assert !((Collection<?>) collection).isEmpty();
		} else {
			assert collection.iterator().hasNext();
		}
		return collection;
	}

	static <T extends Collection<?>> T nonEmpty(T collection) {
		assert collection != null;
		assert !collection.isEmpty();
		return collection;
	}

	static <T> T nonNull(T object) {
		assert object != null;
		return object;
	}

	static void nonNullElements(Object... objects) {
		assert objects != null;
		for (int i = 0; i < objects.length; i++) {
			assert objects[i] != null : i + ". parameter is NULL";
		}
	}

	static void nonNullElements(Object o) {
		assert o != null;
	}

	static void nonNullElements(Object o1, Object o2) {
		assert o1 != null;
		assert o2 != null;
	}

	static void nonNullElements(Object o1, Object o2, Object o3) {
		assert o1 != null;
		assert o2 != null;
		assert o3 != null;
	}

	static void nonNullElements(Object o1, Object o2, Object o3, Object o4) {
		assert o1 != null;
		assert o2 != null;
		assert o3 != null;
		assert o4 != null;
	}

	static void nonNullElements(Object o1, Object o2, Object o3, Object o4, Object o5) {
		assert o1 != null;
		assert o2 != null;
		assert o3 != null;
		assert o4 != null;
		assert o5 != null;
	}

	static <T extends Iterable<?>> T nonNullElements(T iterable) {
		assert iterable != null;
		iterable.forEach(t -> {
			assert t != null;
		});
		return iterable;
	}

	static Require<Object> require() {
		return NOTHING;
	}

	static void that(boolean condition) {
		assert condition;
	}

	static <T> T that(T object, boolean condition) {
		assert condition;
		return object;
	}

	static <T> T that(T object, Predicate<? super T> predicate) {
		assert predicate.test(object);
		return object;
	}

	default <X extends T> Require<X> is(Class<X> type) {
		Require.nonNull(type);
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
		Require.nonNull(predicate);
		return o -> Require.that(apply(o), t -> t == null || predicate.test(t));
	}

	default Require<T> thatNot(Predicate<? super T> predicate) {
		return that(predicate.negate());
	}
}
