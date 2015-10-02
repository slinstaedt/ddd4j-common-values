package org.ddd4j.value.math.primitive;

import static java.util.Objects.requireNonNull;

import org.ddd4j.value.Value;

public final class StringValue extends Value {

	private final String value;

	public StringValue(String value) {
		this.value = requireNonNull(value);
	}

	@Override
	protected String value() {
		return value;
	}
}
