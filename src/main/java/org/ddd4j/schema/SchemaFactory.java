package org.ddd4j.schema;

import java.util.function.Function;

import org.ddd4j.io.ByteDataInput;
import org.ddd4j.schema.Schema.Fingerprint;

public interface SchemaFactory {

	static Class<?> classForName(String className, Function<? super ClassNotFoundException, Class<?>> notFound) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			return notFound.apply(e);
		}
	}

	<T> Schema<T> createSchema(Class<T> type);

	Fingerprint readFingerprint(ByteDataInput input);

	Schema<?> readSchema(ByteDataInput input);
}
