package org.ddd4j.schema.avro;

import java.io.IOException;

import org.apache.avro.Conversions;
import org.apache.avro.reflect.ReflectData;
import org.ddd4j.contract.Require;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.value.collection.Configuration;
import org.ddd4j.value.collection.Props;

public class AvroSchemaFactory implements SchemaFactory {

	private final Configuration configuration;
	private final ReflectData data;

	public AvroSchemaFactory() {
		this(Props.EMTPY);
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
	public Schema<?> readSchema(ReadBuffer buffer) throws IOException {
		return AvroSchema.deserialize(this, buffer);
	}

	public ReflectData getData() {
		return data;
	}

	@Override
	public Configuration getConfiguration() {
		return configuration;
	}
}
