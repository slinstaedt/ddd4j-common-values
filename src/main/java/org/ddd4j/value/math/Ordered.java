package org.ddd4j.value.math;

@FunctionalInterface
public interface Ordered<T extends Ordered<T>> extends Comparable<T> {

	enum Comparison {
		EQUAL, LARGER, SMALLER;

		public static Comparison of(int compareValue) {
			if (compareValue < 0) {
				return SMALLER;
			} else if (compareValue > 0) {
				return LARGER;
			} else {
				return EQUAL;
			}
		}
	}

	default Comparison compare(T other) {
		return Comparison.of(compareTo(other));
	}

	default boolean equal(T other) {
		return compare(other) == Comparison.EQUAL;
	}

	default boolean largerThan(T other) {
		return compare(other) == Comparison.LARGER;
	}

	default boolean notLargerThan(T other) {
		return compare(other) != Comparison.LARGER;
	}

	default boolean notSmallerThan(T other) {
		return compare(other) != Comparison.SMALLER;
	}

	default boolean smallerThan(T other) {
		return compare(other) == Comparison.SMALLER;
	}
}
