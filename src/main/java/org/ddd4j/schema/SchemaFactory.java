package org.ddd4j.schema;

import org.ddd4j.Throwing.TFunction;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.schema.java.ClassBasedSchemaFactory;
import org.ddd4j.spi.Key;
import org.ddd4j.util.Type;
import org.ddd4j.value.Named;

public interface SchemaFactory extends Named {

	Key<SchemaFactory> KEY = Key.of(SchemaFactory.class, ctx -> new ClassBasedSchemaFactory());

	static Class<?> classForName(String className, TFunction<? super ClassNotFoundException, Class<?>> notFound) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			return notFound.apply(e);
		}
	}

	<T> Schema<T> createSchema(Type<T> type);

	Schema<?> readSchema(ReadBuffer buffer);
}
