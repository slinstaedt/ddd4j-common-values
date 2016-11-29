package org.ddd4j.value.collection;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Value;

@FunctionalInterface
public interface Tpl<L, R> {

	class Tuple<L, R> extends Value.Simple<Tuple<L, R>, List<?>> implements Tpl<L, R> {

		private final L left;
		private final R right;

		public Tuple(L left, R right) {
			this.left = left;
			this.right = right;
		}

		@Override
		public <T> T fold(BiFunction<? super L, ? super R, ? extends T> function) {
			return function.apply(left, right);
		}

		@Override
		protected List<?> value() {
			return Arrays.asList(left, right);
		}
	}

	@FunctionalInterface
	interface Void extends Tpl<Void, Void> {

		@Override
		default <X> X fold(BiFunction<? super Void, ? super Void, ? extends X> mapper) {
			throw new NoSuchElementException("Tuple is empty");
		}

		Optional<Void> get();

		@Override
		default <T> T recursiveFold(T identity, BiFunction<Object, ? super T, ? extends T> function) {
			return identity;
		}
	}

	Void EMPTY = Optional::empty;

	static <E> Tpl<E, E> both(E entry) {
		return of(entry, entry);
	}

	static <L extends T, R extends T, T> void consume(Tpl<L, R> tuple, Consumer<T> consumer) {
		tuple.visit((l, r) -> {
			consumer.accept(l);
			consumer.accept(r);
		});
	}

	static <L, R> Tpl<L, R> of(L left, R right) {
		return new Tuple<>(left, right);
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

	default <A, T> T collect(Collector<Object, A, T> collector) {
		A accumulation = collector.supplier().get();
		collect(accumulation, collector.accumulator());
		return collector.finisher().apply(accumulation);
	};

	default <A, T> void collect(T accumulation, BiConsumer<? super T, Object> accumulator) {
		fold((l, r) -> {
			if (l instanceof Tpl) {
				((Tpl<?, ?>) l).collect(accumulation, accumulator);
			} else {
				accumulator.accept(accumulation, l);
			}
			if (r instanceof Tpl) {
				((Tpl<?, ?>) r).collect(accumulation, accumulator);
			} else {
				accumulator.accept(accumulation, r);
			}
			return accumulation;
		});
	}

	default boolean equals(Object left, Object right) {
		return equalsLeft(left) && equalsRight(right);
	}

	default boolean equalsLeft(Object left) {
		return testLeft(l -> Objects.equals(l, left));
	}

	default boolean equalsRight(Object right) {
		return testRight(r -> Objects.equals(r, right));
	}

	default <X> Stream<Tpl<X, R>> flatMapLeft(Function<? super L, ? extends Stream<X>> leftMap) {
		return foldLeft(leftMap).map(this::updateLeft);
	}

	default <Y> Stream<Tpl<L, Y>> flatMapRight(Function<? super R, ? extends Stream<? extends Y>> rightMap) {
		return foldRight(rightMap).map(this::updateRight);
	}

	default Tpl<L, R> flatten() {
		return fold(Tpl::of);
	}

	<T> T fold(BiFunction<? super L, ? super R, ? extends T> function);

	default <T> T foldLeft(Function<? super L, ? extends T> function) {
		Require.nonNull(function);
		return fold((l, r) -> function.apply(l));
	}

	default <T> T foldRight(Function<? super R, ? extends T> function) {
		Require.nonNull(function);
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

	default <T> T recursiveFold(T identity, BiFunction<Object, ? super T, ? extends T> function) {
		BiFunction<Object, T, T> f = (o, t) -> o instanceof Tpl ? ((Tpl<?, ?>) o).recursiveFold(t, function) : function.apply(o, t);
		return fold((l, r) -> f.apply(r, f.apply(l, identity)));
	}

	default Tpl<R, L> reverse() {
		return fold((l, r) -> Tpl.of(r, l));
	}

	default int size() {
		return recursiveFold(0, (o, t) -> t + 1);
	}

	default boolean test(BiPredicate<? super L, ? super R> predicate) {
		return fold(predicate::test);
	}

	default boolean testLeft(Predicate<? super L> predicate) {
		return fold((l, r) -> predicate.test(l));
	}

	default boolean testRight(Predicate<? super R> predicate) {
		return fold((l, r) -> predicate.test(r));
	}

	default Object[] toArray() {
		return collect(Collectors.toList()).toArray();
	}

	default <X> Tpl<X, R> updateLeft(X left) {
		return mapLeft(l -> left);
	}

	default <Y> Tpl<L, Y> updateRight(Y right) {
		return mapRight(r -> right);
	}

	default Tpl<L, R> visit(BiConsumer<? super L, ? super R> consumer) {
		return fold((l, r) -> {
			consumer.accept(l, r);
			return this;
		});
	}

	default Tpl<L, R> visitLeft(Consumer<? super L> consumer) {
		return visit((l, r) -> {
			consumer.accept(l);
		});
	}

	default Tpl<L, R> visitRight(Consumer<? super R> consumer) {
		return visit((l, r) -> {
			consumer.accept(r);
		});
	}
}
