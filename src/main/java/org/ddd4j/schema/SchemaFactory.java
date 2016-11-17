package org.ddd4j.schema;

import org.ddd4j.io.ByteDataInput;
import org.ddd4j.schema.Schema.Fingerprint;
import org.ddd4j.value.Throwing;

public interface SchemaFactory {

	static Class<?> classForName(String className) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			return Throwing.unchecked(e);
		}
	}

	<T> Schema<T> createSchema(Class<T> type);

	Fingerprint readFingerprint(ByteDataInput input);

	Schema<?> readSchema(ByteDataInput input);
}
