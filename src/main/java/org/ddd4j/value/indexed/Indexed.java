package org.ddd4j.value.indexed;

import java.util.function.Function;

import org.ddd4j.value.Type;

public interface Indexed {

	static <D> Indexer<D> definition(Indexer.Capability... capabilities) {
		return new Indexer<>(capabilities);
	}

	static <D, T> Property<D, T> property(String name, Type<T> type, Function<? super D, T> mapper, Property.Capability... capabilities) {
		return new Property<>(name, type, mapper, capabilities);
	}
}