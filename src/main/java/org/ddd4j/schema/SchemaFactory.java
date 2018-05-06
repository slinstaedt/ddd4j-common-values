package org.ddd4j.schema;

import org.ddd4j.io.ReadBuffer;
import org.ddd4j.schema.java.ClassBasedSchemaFactory;
import org.ddd4j.spi.Ref;
import org.ddd4j.util.Throwing.TFunction;
import org.ddd4j.util.Type;
import org.ddd4j.util.value.Named;
import org.ddd4j.value.config.ConfKey;

public interface SchemaFactory extends Named {

	Ref<SchemaFactory> REF = Ref.of(SchemaFactory.class, ctx -> new ClassBasedSchemaFactory());
	ConfKey<String[]> KEY = ConfKey.ofStrings("schemaFactories");

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
