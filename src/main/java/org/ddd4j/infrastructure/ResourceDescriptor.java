package org.ddd4j.infrastructure;

import java.util.regex.Pattern;

import org.ddd4j.Require;
import org.ddd4j.value.Value;

public class ResourceDescriptor extends Value.StringBased<ResourceDescriptor> {

	public static final ResourceDescriptor ALL = new ResourceDescriptor("*");
	private static final Pattern ALLOWED = Pattern.compile("\\w{2,30}");

	public static ResourceDescriptor of(String value) {
		return new ResourceDescriptor(Require.that(value, ALLOWED.matcher(value).matches()));
	}

	public ResourceDescriptor(String value) {
		super(value);
	}

	public boolean isAll() {
		return equals(ALL);
	}
}
