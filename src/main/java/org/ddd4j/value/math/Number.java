package org.ddd4j.value.math;

public interface Number<T extends Number<T>> extends Numeric<T>, Ordered<T> {

	default T absolute() {
		return sign().apply(self());
	}

	T fractionalPart();

	T wholePart();
}
