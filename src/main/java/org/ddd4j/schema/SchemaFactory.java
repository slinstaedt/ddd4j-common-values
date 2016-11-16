package org.ddd4j.schema;

import org.ddd4j.io.ByteDataInput;
import org.ddd4j.schema.Schema.Fingerprint;

public interface SchemaFactory {

	static Class<?> classForName(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException("Could not load class with name: " + name, e);
		}
	}

	<T> Schema<T> createSchema(Class<T> type);

	Fingerprint readFingerprint(ByteDataInput input);

	Schema<?> readSchema(ByteDataInput input);
}
