package org.ddd4j.value.collection;

import static java.util.Objects.requireNonNull;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Function;

@FunctionalInterface
public interface Tpl<L, R> {

	@FunctionalInterface
	interface Void extends Tpl<Void, Void> {

		@Override
		default <X> X fold(BiFunction<? super Void, ? super Void, ? extends X> mapper) {
			throw new NoSuchElementException("Tuple is empty");
		}

		Optional<Void> get();
	}

	Void EMPTY = Optional::empty;

	static <L, R> Tpl<L, R> of(L left, R right) {
		return new Tpl<L, R>() {

			@Override
			public <T> T fold(BiFunction<? super L, ? super R, ? extends T> function) {
				return function.apply(left, right);
			}
		};
	}

	static <L> Tpl<L, Void> ofLeft(L left) {
		return Tpl.of(left, EMPTY);
	}

	static <R> Tpl<Void, R> ofRight(R right) {
		return Tpl.of(EMPTY, right);
	}

	default <T> Tpl<Tpl<L, R>, T> append(T entry) {
		return Tpl.of(this, entry);
	}

	default String asString() {
		return fold((l, r) -> "<" + l + "," + r + ">");
	}

	default void consume(BiConsumer<? super L, ? super R> consumer) {
		fold((l, r) -> {
			consumer.accept(l, r);
			return null;
		});
	}

	<T> T fold(BiFunction<? super L, ? super R, ? extends T> function);

	default <T> T foldLeft(Function<? super L, ? extends T> function) {
		requireNonNull(function);
		return fold((l, r) -> function.apply(l));
	}

	default <T> T foldRight(Function<? super R, ? extends T> function) {
		requireNonNull(function);
		return fold((l, r) -> function.apply(r));
	}

	default L getLeft() {
		return foldLeft(Function.identity());
	}

	default R getRight() {
		return foldRight(Function.identity());
	}

	default boolean isEqual() {
		return fold(Objects::equals);
	}

	default <X, Y> Tpl<X, Y> map(Function<? super L, ? extends X> leftMap, Function<? super R, ? extends Y> rightMap) {
		requireNonNull(leftMap);
		requireNonNull(rightMap);
		return fold((l, r) -> Tpl.of(leftMap.apply(l), rightMap.apply(r)));
	}

	default <X> Tpl<X, R> mapLeft(Function<? super L, ? extends X> leftMap) {
		return map(leftMap, Function.identity());
	}

	default <Y> Tpl<L, Y> mapRight(Function<? super R, ? extends Y> rightMap) {
		return map(Function.identity(), rightMap);
	}

	default <T> Tpl<T, Tpl<L, R>> prepend(T entry) {
		return Tpl.of(entry, this);
	}

	default <T> T recursiveFold(T id, BiFunction<Object, ? super T, ? extends T> f, BinaryOperator<T> op) {
	}

	default Tpl<R, L> reverse() {
		return fold((l, r) -> Tpl.of(r, l));
	}

	default int size() {
		// TODO
		return 0;
	}

	default boolean test(BiPredicate<? super L, ? super R> predicate) {
		return fold(predicate::test);
	}

	default Object[] toArray() {
		// TODO
		return null;
	}
}
