package org.ddd4j.schema;

import java.io.IOException;

import org.ddd4j.io.Input;
import org.ddd4j.spi.Service;
import org.ddd4j.value.Throwing.TFunction;

public interface SchemaFactory extends Service {

	static Class<?> classForName(String className, TFunction<? super ClassNotFoundException, Class<?>> notFound) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			return notFound.apply(e);
		}
	}

	<T> Schema<T> createSchema(Class<T> type);

	Schema<?> readSchema(Input input) throws IOException;
}
