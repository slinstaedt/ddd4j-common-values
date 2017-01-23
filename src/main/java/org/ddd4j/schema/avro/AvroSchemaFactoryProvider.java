package org.ddd4j.schema.avro;

import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.schema.SchemaFactoryProvider;
import org.ddd4j.spi.ServiceLocator;
import org.ddd4j.value.collection.Configuration;

public class AvroSchemaFactoryProvider implements SchemaFactoryProvider {

	@Override
	public SchemaFactory provideService(Configuration configuration, ServiceLocator locator) {
		return new AvroSchemaFactory(configuration);
	}
}
