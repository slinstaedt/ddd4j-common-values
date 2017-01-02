package org.ddd4j.infrastructure;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Value;

public class ResourceDescriptor extends Value.StringBased<ResourceDescriptor> {

	private static final int MAX_DESCRIPTOR_LENGTH = 30;

	public ResourceDescriptor(String value) {
		super(Require.that(value, value != null && !value.isEmpty() && value.length() < MAX_DESCRIPTOR_LENGTH));
	}
}
