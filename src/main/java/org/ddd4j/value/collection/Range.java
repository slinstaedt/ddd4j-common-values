package org.ddd4j.value.collection;

import org.ddd4j.value.math.Enumerable;
import org.ddd4j.value.math.primitive.IntValue;

public interface Range<T extends Enumerable<T>> {

	static <T extends Enumerable<T>> Range<T> measure(T first, int size) {
		return null;
	}

	static <T extends Enumerable<T>> Range<T> span(T first, T last) {
		return null;
	}

	boolean containsElement(T element);

	default boolean decreasing() {
		return !increasing();
	}

	boolean includesRange(Range<T> other);

	boolean increasing();

	Range<T> shifted(IntValue shift);
}
