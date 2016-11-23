package org.ddd4j.schema.avro;

import java.io.IOException;

import org.apache.avro.Conversions;
import org.apache.avro.reflect.ReflectData;
import org.ddd4j.contract.Require;
import org.ddd4j.io.Input;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.spi.Configuration;

public class AvroSchemaFactory implements SchemaFactory {

	private final Configuration configuration;
	private final ReflectData data;

	public AvroSchemaFactory() {
		this(Configuration.NONE);
	}

	public AvroSchemaFactory(Configuration configuration) {
		this.configuration = Require.nonNull(configuration);
		this.data = new ReflectData();
		// TODO make configurable?
		data.addLogicalTypeConversion(new Conversions.UUIDConversion());
	}

	@Override
	public <T> Schema<T> createSchema(Class<T> type) {
		return new AvroSchema<>(this, type, data.getSchema(type));
	}

	@Override
	public Schema<?> readSchema(Input input) throws IOException {
		return AvroSchema.deserialize(this, input);
	}

	public ReflectData getData() {
		return data;
	}

	@Override
	public String getServiceName() {
		return "avro";
	}

	@Override
	public Configuration getConfiguration() {
		return configuration;
	}
}
