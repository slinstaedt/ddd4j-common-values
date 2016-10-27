package org.ddd4j.value;

import static java.util.Objects.requireNonNull;

import org.ddd4j.value.math.Invertible;
import org.ddd4j.value.math.primitive.AbstractValue;
import org.ddd4j.value.math.primitive.IntValue;

public class TestValue extends AbstractValue implements Invertible.Wrapper<TestValue, IntValue> {

	private final IntValue value;

	public TestValue(IntValue value) {
		this.value = requireNonNull(value);
	}

	@Override
	public TestValue self() {
		return this;
	}

	@Override
	public IntValue value() {
		return value;
	}

	@Override
	public TestValue wrap(IntValue value) {
		return new TestValue(value);
	}
}
