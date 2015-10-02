package org.ddd4j.value.owner;

import java.util.function.Predicate;

public interface Contract<S, T> {

	Object party1();

	Object party2();

	void sign(Predicate<Object> sign);

	Property.Owned<S> subject1();

	Property.Owned<T> subject2();
}