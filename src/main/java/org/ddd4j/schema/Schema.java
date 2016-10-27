package org.ddd4j.schema;

import java.util.Optional;

import org.ddd4j.value.Value;

public interface Schema {

	interface Factory {

		Optional<? extends Schema> lookup(Fingerprint fingerprint);

		Schema schema(Type<?> type);
	}

	interface Fingerprint extends Value<Fingerprint> {
	}

	@FunctionalInterface
	interface Type<T> {

		Optional<T> cast(Object object);

		default boolean partOf(Schema schema) {
			return schema.contains(this);
		}
	}

	String getName();

	Fingerprint getFingerprint();

	boolean contains(Type<?> type);
}
