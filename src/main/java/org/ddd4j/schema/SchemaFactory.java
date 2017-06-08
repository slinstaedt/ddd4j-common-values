package org.ddd4j.schema;

import org.ddd4j.Throwing.TFunction;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Key;

public interface SchemaFactory {

	Key<SchemaFactory> KEY = Key.of(SchemaFactory.class);

	static Class<?> classForName(String className, TFunction<? super ClassNotFoundException, Class<?>> notFound) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			return notFound.apply(e);
		}
	}

	// TODO use Type here?
	<T> Schema<T> createSchema(Class<T> type);

	Schema<?> readSchema(ReadBuffer buffer);
}
