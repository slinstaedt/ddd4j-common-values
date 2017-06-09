package org.ddd4j.value.indexed;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.value.Type;

public class Property<D, T> {

	public enum Capability {
		INDEXED, STORED;
	}

	private final String name;
	private final Type<T> type;
	private final Function<? super D, T> mapper;
	private final Set<Capability> capabilities;

	Property(String name, Type<T> type, Function<? super D, T> mapper, Capability... capabilities) {
		this.name = Require.nonEmpty(name);
		this.type = Require.nonNull(type);
		this.mapper = Require.nonNull(mapper);
		this.capabilities = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(capabilities)));
	}

	public Set<Capability> getCapabilities() {
		return capabilities;
	}

	public String getName() {
		return name;
	}

	public Type<T> getType() {
		return type;
	}

	public T readValue(D document) {
		return mapper.apply(document);
	}
}