package org.ddd4j.value.math;

import org.ddd4j.util.Self;
import org.ddd4j.value.ValueWrapper;

public interface Binary<T extends Binary<T>> extends Self<T> {

	interface Wrapper<T extends Wrapper<T, V>, V extends Binary<V>> extends ValueWrapper<T, V>, Binary<T> {

		@Override
		default T and(T other) {
			return apply(V::and, other);
		}

		@Override
		default T flip(int index) {
			return applyFunction(V::flip, index);
		}

		@Override
		default boolean get(int index) {
			return value().get(index);
		}

		@Override
		default T leftLogicalShift(int shift) {
			return applyFunction(V::leftLogicalShift, shift);
		}

		@Override
		default T not() {
			return apply(V::not);
		}

		@Override
		default T or(T other) {
			return apply(V::or, other);
		}

		@Override
		default T rightArithmeticShift(int shift) {
			return applyFunction(V::rightArithmeticShift, shift);
		}

		@Override
		default T rightLogicalShift(int shift) {
			return applyFunction(V::rightLogicalShift, shift);
		}

		@Override
		default T set(int index) {
			return applyFunction(V::set, index);
		}

		@Override
		default T set(int index, boolean bit) {
			return bit ? set(index) : unset(index);
		}

		@Override
		default T unset(int index) {
			return applyFunction(V::unset, index);
		}

		@Override
		default T xor(T other) {
			return apply(V::xor, other);
		}
	}

	T and(T other);

	T flip(int index);

	boolean get(int index);

	T leftLogicalShift(int shift);

	T not();

	T or(T other);

	T rightArithmeticShift(int shift);

	T rightLogicalShift(int shift);

	default T set(int index) {
		return set(index, true);
	}

	T set(int index, boolean bit);

	default T unset(int index) {
		return set(index, false);
	}

	T xor(T other);
}
