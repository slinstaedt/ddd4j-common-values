package org.ddd4j.value.collection;

import static java.util.Objects.requireNonNull;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@FunctionalInterface
public interface Tpl<L, R> {

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

	static <L extends T, R extends T, T> void consume(Tpl<L, R> tuple, Consumer<T> consumer) {
		tuple.consume((l, r) -> {
			consumer.accept(l);
			consumer.accept(r);
		});
	}

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

	default <T> T recursiveFold(T identity, BiFunction<Object, ? super T, ? extends T> function) {
		BiFunction<Object, T, T> f = (o, t) -> o instanceof Tpl ? ((Tpl<?, ?>) o).recursiveFold(t, function) : function
				.apply(o, t);
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

	default Object[] toArray() {
		return collect(Collectors.toList()).toArray();
	}
}
