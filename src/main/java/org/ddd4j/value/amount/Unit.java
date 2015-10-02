package org.ddd4j.value.amount;

public interface Unit<Q extends Quantity> {

	<T> T to(Unit<Q> unit);
}
