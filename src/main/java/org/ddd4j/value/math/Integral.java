package org.ddd4j.value.math;

public interface Integral<T extends Integral<T>> extends Number<T>, Enumerable<T> {

	/**
	 * Determine if this number is a factor of the given number.
	 */
	default boolean devides(T other) {
		return other.divided(self()).isZero();
	}

	/**
	 * Determine if the number is the multiplicative identity.
	 */
	boolean isUnit();

	/**
	 * Determine if the number is the additive identity.
	 */
	boolean isZero();

	/**
	 * The remainder, after dividing this number by the given number.
	 */
	T remainder(T other);
}
