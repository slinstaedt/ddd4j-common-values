package org.ddd4j.schema.avro;

import java.io.IOException;

import org.apache.avro.Schema.Parser;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.Encoder;
import org.ddd4j.contract.Require;
import org.ddd4j.io.Input;
import org.ddd4j.io.Output;
import org.ddd4j.schema.Fingerprint;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.Value;

public class AvroSchema<T> extends Value.Simple<Schema<T>> implements Schema<T> {

	static AvroSchema<?> deserialize(AvroSchemaFactory factory, Input input) throws IOException {
		org.apache.avro.Schema writerSchema = new Parser().parse(input.asDataInput().readUTF());
		Class<?> type = factory.getData().getClass(writerSchema);
		if (type == null || type == Object.class) {
			type = SchemaFactory.classForName(writerSchema.getFullName(), e -> Record.class);
		}
		return new AvroSchema<>(factory, type, writerSchema);
	}

	private final AvroSchemaFactory factory;
	private final Class<T> type;
	private final org.apache.avro.Schema writerSchema;

	public AvroSchema(AvroSchemaFactory factory, Class<T> type, org.apache.avro.Schema schema) {
		this.factory = Require.nonNull(factory);
		this.type = Require.nonNull(type);
		this.writerSchema = Require.nonNull(schema);
	}

	@Override
	public String getName() {
		return writerSchema.getFullName();
	}

	@Override
	public Fingerprint getFingerprint() {
		AvroFingerprintAlgorithm algorithm = factory.getConfiguration()
				.getEnum(AvroFingerprintAlgorithm.class, "fingerprint")
				.orElse(AvroFingerprintAlgorithm.SHA_256);
		return algorithm.parsingFingerprint(writerSchema);
	}

	@Override
	public void serialize(Output output) throws IOException {
		output.asDataOutput().writeUTF(writerSchema.toString());
	}

	@Override
	public boolean equal(Object o1, Object o2) {
		return factory.getData().compare(o1, o2, writerSchema) == 0;
	}

	@Override
	public int hashCode(Object object) {
		return factory.getData().hashCode(object, writerSchema);
	}

	private AvroCoder coder() {
		return factory.getConfiguration().getEnum(AvroCoder.class, "coding", AvroCoder.JSON);
	}

	@Override
	public Reader<T> createReader(Input input) {
		DatumReader<?> reader;
		if (type == Record.class) {
			reader = new GenericDatumReader<>(writerSchema, writerSchema, factory.getData());
		} else {
			org.apache.avro.Schema readerSchema = factory.getData().getSchema(type);
			reader = factory.getData().createDatumReader(writerSchema, readerSchema);
		}
		Decoder decoder = coder().createDecoder(writerSchema, input.asStream());
		return Throwing.ofSupplied(() -> reader.read(null, decoder)).map(type::cast)::get;
	}

	@Override
	public Writer<T> createWriter(Output output) {
		Encoder encoder = coder().createEncoder(writerSchema, output.asStream());
		@SuppressWarnings("unchecked")
		DatumWriter<Object> writer = factory.getData().createDatumWriter(writerSchema);
		return (datum, flush) -> {
			if (flush) {
				encoder.flush();
			} else {
				writer.write(datum, encoder);
			}
		};
	}

	@Override
	public boolean compatibleWith(Schema<?> existing) {
		return existing.<AvroSchema>as(AvroSchema.class)
				.mapNonNull(o -> SchemaCompatibility.checkReaderWriterCompatibility(writerSchema, o.writerSchema).getType())
				.checkEqual(SchemaCompatibilityType.COMPATIBLE);
	}

	@Override
	protected Object value() {
		return writerSchema;
	}
}