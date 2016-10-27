package org.ddd4j.value.math.primitive;

import static java.util.Objects.requireNonNull;

public final class StringValue extends AbstractValue {

	private final String value;

	public StringValue(String value) {
		this.value = requireNonNull(value);
	}

	@Override
	protected String value() {
		return value;
	}
}
