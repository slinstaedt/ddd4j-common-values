package org.ddd4j.value.function;

import java.util.function.Function;

import org.ddd4j.value.collection.Tpl;

@FunctionalInterface
public interface Bijection<T, R> {

	default R apply1(T t) {
		return projections().foldLeft(f -> f.apply(t));
	}

	default T apply2(R r) {
		return projections().foldRight(f -> f.apply(r));
	}

	Tpl<Function<T, R>, Function<R, T>> projections();
}
