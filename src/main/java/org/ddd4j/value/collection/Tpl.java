package org.ddd4j.value.collection;

import static java.util.Objects.requireNonNull;

import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Tpl<L, R> {

	public static class Void extends Tpl<Void, Void> {

		public Void() {
			super(null, null);
		}

		@Override
		public <X> X fold(BiFunction<? super Void, ? super Void, ? extends X> mapper) {
			throw new NoSuchElementException("Tuple is empty");
		}
	}

	public static final Void EMPTY = new Void();

	public static <L, R> Tpl<L, R> of(L left, R right) {
		return new Tpl<>(left, right);
	}

	public static <L> Tpl<L, Void> ofLeft(L left) {
		return new Tpl<>(left, EMPTY);
	}

	public static <R> Tpl<Void, R> ofRight(R right) {
		return new Tpl<>(EMPTY, right);
	}

	private final L left;
	private final R right;

	public Tpl(L left, R right) {
		this.left = left;
		this.right = right;
	}

	public <T> Tpl<Tpl<L, R>, T> append(T entry) {
		return new Tpl<>(this, entry);
	}

	public <T> T fold(BiFunction<? super L, ? super R, ? extends T> mapper) {
		return mapper.apply(left, right);
	}

	public <T> T foldLeft(Function<? super L, ? extends T> mapper) {
		requireNonNull(mapper);
		return fold((l, r) -> mapper.apply(l));
	}

	public <T> T foldRight(Function<? super R, ? extends T> mapper) {
		requireNonNull(mapper);
		return fold((l, r) -> mapper.apply(r));
	}

	public <X, Y> Tpl<X, Y> map(Function<? super L, ? extends X> leftMap, Function<? super R, ? extends Y> rightMap) {
		requireNonNull(leftMap);
		requireNonNull(rightMap);
		return fold((l, r) -> new Tpl<>(leftMap.apply(l), rightMap.apply(r)));
	}

	public <X> Tpl<X, R> mapLeft(Function<? super L, ? extends X> leftMap) {
		return map(leftMap, Function.identity());
	}

	public <Y> Tpl<L, Y> mapRight(Function<? super R, ? extends Y> rightMap) {
		return map(Function.identity(), rightMap);
	}

	public <T> Tpl<T, Tpl<L, R>> prepend(T entry) {
		return new Tpl<>(entry, this);
	}

	public int size() {
		// TODO
		return 2;
	}

	public Object[] toArray() {
		// TODO
		return null;
	}
}
