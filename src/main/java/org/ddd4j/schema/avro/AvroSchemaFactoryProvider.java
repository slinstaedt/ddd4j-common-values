package org.ddd4j.schema.avro;

import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.schema.SchemaFactoryProvider;
import org.ddd4j.spi.Configuration;

public class AvroSchemaFactoryProvider implements SchemaFactoryProvider {

	@Override
	public SchemaFactory provideService(Configuration configuration) {
		return new AvroSchemaFactory(configuration);
	}
}
