package org.ddd4j.function;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.value.Opt;
import org.ddd4j.value.collection.Tpl;

@FunctionalInterface
public interface BidiFunction<T, U> {

	static <T, U> BidiFunction<T, U> nullMapping() {
		return fn -> Tpl.of(null, null);
	}

	static <T, U> BidiFunction<T, U> ofFallbacks(T t, U u) {
		return fn -> Tpl.of(t, u);
	}

	Tpl<T, U> apply(BiFunction<Function<T, Tpl<T, U>>, Function<U, Tpl<T, U>>, Tpl<T, U>> fn);

	default Tpl<T, U> applyLeft(Function<Function<T, Tpl<T, U>>, Tpl<T, U>> fn) {
		return apply((f1, f2) -> fn.apply(f1));
	}

	default Tpl<T, U> applyLeftValue(T t) {
		return applyLeft(fn -> fn.apply(t));
	}

	default Tpl<T, U> applyRight(Function<Function<U, Tpl<T, U>>, Tpl<T, U>> fn) {
		return apply((f1, f2) -> fn.apply(f2));
	}

	default Tpl<T, U> applyRightValue(U u) {
		return applyRight(fn -> fn.apply(u));
	}

	default Function<T, U> asLeftFunction() {
		return t -> applyLeftValue(t).getRight();
	}

	default Function<U, T> asRightFunction() {
		return u -> applyRightValue(u).getLeft();
	}

	default BidiFunction<T, U> withMapping(T left, U right) {
		return fn -> fn.apply(t -> Objects.equals(t, left) ? Tpl.of(t, right) : apply(fn), u -> Objects.equals(u, right) ? Tpl.of(left, u) : apply(fn));
	}

	default BidiFunction<T, U> withTriedFirst(Function<T, Opt<U>> f1, Function<U, Opt<T>> f2) {
		Require.nonNulls(f1, f2);
		return fn -> fn.apply(t -> f1.apply(t).mapNullable(u -> Tpl.of(t, u)).orElseGet(() -> apply(fn)),
				u -> f2.apply(u).mapNullable(t -> Tpl.of(t, u)).orElseGet(() -> apply(fn)));
	}

	default BidiFunction<T, U> withTriedLeftFirst(Function<T, Opt<U>> function) {
		return withTriedFirst(function, u -> Opt.none());
	}

	default BidiFunction<T, U> withTriedRightFirst(Function<U, Opt<T>> function) {
		return withTriedFirst(t -> Opt.none(), function);
	}
}