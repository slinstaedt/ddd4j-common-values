package org.ddd4j.infrastructure;

import java.util.regex.Pattern;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Value;

public class ResourceDescriptor extends Value.StringBased<ResourceDescriptor> {

	private static final Pattern ALLOWED = Pattern.compile("\\w{2,30}");

	public static ResourceDescriptor of(String value) {
		return new ResourceDescriptor(value);
	}

	public ResourceDescriptor(String value) {
		super(Require.that(value, ALLOWED.matcher(value).matches()));
	}
}
