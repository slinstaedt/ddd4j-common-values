package org.ddd4j.schema.avro;

import org.apache.avro.Schema.Parser;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.Encoder;
import org.ddd4j.Require;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.schema.Fingerprint;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.value.Value;

public class AvroSchema<T> extends Value.Simple<Schema<T>, org.apache.avro.Schema> implements Schema<T> {

	static AvroSchema<?> deserialize(AvroSchemaFactory factory, ReadBuffer buffer) {
		org.apache.avro.Schema writerSchema = new Parser().parse(buffer.getUTF());
		Class<?> type = factory.getData().getClass(writerSchema);
		if (type == null || type == Object.class) {
			type = SchemaFactory.classForName(writerSchema.getFullName(), e -> Record.class);
		}
		return new AvroSchema<>(factory, writerSchema);
	}

	private final AvroSchemaFactory factory;
	private final org.apache.avro.Schema writerSchema;

	public AvroSchema(AvroSchemaFactory factory, org.apache.avro.Schema schema) {
		this.factory = Require.nonNull(factory);
		this.writerSchema = Require.nonNull(schema);
	}

	@Override
	public boolean compatibleWith(Schema<?> existing) {
		return existing.<AvroSchema> as(AvroSchema.class)
				.mapNonNull(o -> SchemaCompatibility.checkReaderWriterCompatibility(writerSchema, o.writerSchema).getType())
				.checkEqual(SchemaCompatibilityType.COMPATIBLE);
	}

	@Override
	public <X> Reader<X> createReader(ReadBuffer buffer, Class<X> targetType) {
		DatumReader<?> reader;
		if (targetType == Record.class) {
			reader = new GenericDatumReader<>(writerSchema, writerSchema, factory.getData());
		} else {
			org.apache.avro.Schema readerSchema = factory.getData().getSchema(targetType);
			reader = factory.getData().createDatumReader(writerSchema, readerSchema);
		}
		Decoder decoder = factory.createDecoder(writerSchema, buffer.asInputStream());
		return () -> targetType.cast(reader.read(null, decoder));
	}

	@Override
	public Writer<T> createWriter(WriteBuffer buffer) {
		Encoder encoder = factory.createEncoder(writerSchema, buffer.asOutputStream());
		@SuppressWarnings("unchecked")
		DatumWriter<Object> writer = factory.getData().createDatumWriter(writerSchema);
		return v -> writer.write(v, encoder);
	}

	@Override
	public boolean equal(Object o1, Object o2) {
		return factory.getData().compare(o1, o2, writerSchema) == 0;
	}

	@Override
	public Fingerprint getFingerprint() {
		return factory.getFingerprintAlgorithm().parsingFingerprint(writerSchema);
	}

	@Override
	public String getName() {
		return writerSchema.getFullName();
	}

	@Override
	public int hashCode(Object object) {
		return factory.getData().hashCode(object, writerSchema);
	}

	@Override
	public void serialize(WriteBuffer buffer) {
		buffer.putUTF(writerSchema.toString());
	}

	@Override
	protected org.apache.avro.Schema value() {
		return writerSchema;
	}
}