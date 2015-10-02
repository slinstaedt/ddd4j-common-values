package org.ddd4j.value.math;

import org.ddd4j.value.math.Invertible.Sign;

public interface Enumerable<T extends Enumerable<T>> extends Comparable<T> {

	T neighbour(int offset);

	int offset(T other);

	Sign offsetSign(T other);

	default T predecessor() {
		return neighbour(-1);
	}

	default T successor() {
		return neighbour(1);
	}
}
