package org.ddd4j.infrastructure.domain.value;

import java.util.regex.Pattern;

import org.ddd4j.Require;
import org.ddd4j.value.Value;

public class ChannelName extends Value.StringBased<ChannelName> {

	public static final ChannelName ALL = new ChannelName("*");
	private static final Pattern ALLOWED = Pattern.compile("\\w{2,30}");

	public static ChannelName of(String value) {
		return new ChannelName(Require.that(value, ALLOWED.matcher(value).matches()));
	}

	private ChannelName(String value) {
		super(value);
	}

	public boolean isAll() {
		return equals(ALL);
	}
}
