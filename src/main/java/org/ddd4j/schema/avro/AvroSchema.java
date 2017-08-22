package org.ddd4j.schema.avro;

import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecord;
import org.ddd4j.Require;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.schema.Fingerprint;
import org.ddd4j.schema.Schema;
import org.ddd4j.value.Type;
import org.ddd4j.value.Value;

public class AvroSchema<T> extends Value.Simple<Schema<T>, org.apache.avro.Schema> implements Schema<T> {

	private final AvroSchemaFactory factory;
	private final AvroCoder writerCoder;
	private final org.apache.avro.Schema writerSchema;

	public AvroSchema(AvroSchemaFactory factory, AvroCoder coder, org.apache.avro.Schema schema) {
		this.factory = Require.nonNull(factory);
		this.writerCoder = Require.nonNull(coder);
		this.writerSchema = Require.nonNull(schema);
	}

	@Override
	public boolean compatibleWith(Schema<?> existing) {
		return existing.<AvroSchema>as(AvroSchema.class)
				.map(o -> SchemaCompatibility.checkReaderWriterCompatibility(writerSchema, o.writerSchema).getType())
				.map(SchemaCompatibilityType.COMPATIBLE::equals)
				.orElse(Boolean.FALSE);
	}

	@Override
	public <X> Reader<X> createReader(Type<X> readerType) {
		Class<X> type = readerType.getRawType();
		DatumReader<?> reader;
		if (SpecificRecord.class.isAssignableFrom(type)) {
			reader = new SpecificDatumReader<>(type);
		} else if (GenericRecord.class.isAssignableFrom(type)) {
			reader = new GenericDatumReader<>(writerSchema, writerSchema, factory.getData());
		} else {
			org.apache.avro.Schema readerSchema = factory.getData().getSchema(type);
			reader = factory.getData().createDatumReader(writerSchema, readerSchema);
		}
		return buf -> readerType.cast(reader.read(null, factory.createDecoder(writerCoder, writerSchema, buf.asInputStream())));
	}

	@Override
	public Writer<T> createWriter() {
		@SuppressWarnings("unchecked")
		DatumWriter<Object> writer = factory.getData().createDatumWriter(writerSchema);
		return (buf, val) -> writer.write(val, factory.createEncoder(writerSchema, buf.asOutputStream()));
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
	public String getFactoryName() {
		return factory.name();
	}

	@Override
	public int hashCode(Object object) {
		return factory.getData().hashCode(object, writerSchema);
	}

	@Override
	public void serialize(WriteBuffer buffer) {
		buffer.putInt(writerCoder.ordinal());
		buffer.putUTF(writerSchema.toString());
	}

	@Override
	protected org.apache.avro.Schema value() {
		return writerSchema;
	}
}