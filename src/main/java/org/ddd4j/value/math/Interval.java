package org.ddd4j.value.math;

import java.util.function.BiFunction;

import org.ddd4j.Require;

@FunctionalInterface
public interface Interval<T extends Ordered<T>> {

	@FunctionalInterface
	public interface Range<T extends Integral<T>> extends Interval<T> {

		default T distance() {
			return visit((s, t) -> t.minus(s));
		}
	}

	static <T extends Ordered<T>> Interval<T> of(T start, T end) {
		Require.nonNulls(start, end);
		Require.that(start.notLargerThan(end));
		return new Interval<T>() {

			@Override
			public <X> X visit(BiFunction<? super T, ? super T, X> mapper) {
				return mapper.apply(start, end);
			}
		};
	}

	<X> X visit(BiFunction<? super T, ? super T, X> mapper);

	default boolean contains(T element) {
		return visit((s, t) -> s.notLargerThan(element) && t.notSmallerThan(element));
	}

	default Interval<T> startingWith(T start) {
		return of(start, getEnd());
	}

	default Interval<T> endingWith(T end) {
		return of(getStart(), end);
	}

	default T getStart() {
		return visit((s, t) -> s);
	}

	default T getEnd() {
		return visit((s, t) -> s);
	}
}
