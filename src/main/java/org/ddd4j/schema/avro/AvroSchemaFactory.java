package org.ddd4j.schema.avro;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.function.BiFunction;

import org.apache.avro.Schema.Parser;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectData;
import org.ddd4j.contract.Require;
import org.ddd4j.io.ByteDataInput;
import org.ddd4j.io.ByteDataOutput;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.Schema.Fingerprint;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.Throwing.TBiFunction;
import org.ddd4j.value.Value;

public class AvroSchemaFactory implements SchemaFactory {

	public enum FingerprintAlgorithm {
		CRC_64_AVRO("CRC-64-AVRO"), MD5("MD5"), SHA_256("SHA-256");

		private final String value;

		FingerprintAlgorithm(String value) {
			this.value = Require.nonEmpty(value);
		}

		Fingerprint parsingFingerprint(org.apache.avro.Schema schema) {
			try {
				return new AvroFingerprint(SchemaNormalization.parsingFingerprint(value, schema));
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException("Could not create fingerprint with algorithm " + value, e);
			}
		}
	}

	static class AvroSchema<T> extends Value.Simple<Schema<T>> implements Schema<T> {

		static AvroSchema<?> deserialize(AvroSchemaFactory factory, ByteDataInput input) {
			org.apache.avro.Schema writerSchema = new Parser().parse(input.readUTF());
			Class<?> type = factory.data.getClass(writerSchema);
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
			return factory.algorithm.parsingFingerprint(writerSchema);
		}

		@Override
		public void serialize(ByteDataOutput output) {
			output.writeUTF(writerSchema.toString());
		}

		@Override
		public boolean equal(Object o1, Object o2) {
			return factory.data.compare(o1, o2, writerSchema) == 0;
		}

		@Override
		public int hashCode(Object object) {
			return factory.data.hashCode(object, writerSchema);
		}

		@Override
		public Reader<T> createReader(ByteDataInput input) {
			DatumReader<?> reader;
			if (type == Record.class) {
				reader = new GenericDatumReader<>(writerSchema, writerSchema, factory.data);
			} else {
				org.apache.avro.Schema readerSchema = factory.data.getSchema(type);
				reader = factory.data.createDatumReader(writerSchema, readerSchema);
			}
			Decoder decoder = factory.decoderFactory.apply(writerSchema, input.asStream());
			return Throwing.ofSupplied(() -> reader.read(null, decoder)).map(type::cast)::get;
		}

		@Override
		public Writer<T> createWriter(ByteDataOutput output) {
			Encoder encoder = factory.encoderFactory.apply(writerSchema, output.asStream());
			@SuppressWarnings("unchecked")
			DatumWriter<Object> writer = factory.data.createDatumWriter(writerSchema);
			return o -> o.visitNullable(Throwing.ofConsumed(t -> writer.write(t, encoder)), Throwing.ofRunning(encoder::flush));
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

	static class AvroFingerprint extends Value.Simple<Fingerprint> implements Fingerprint {

		static AvroFingerprint deserialize(ByteDataInput input) {
			return new AvroFingerprint(input.readByteArray());
		}

		private final byte[] value;

		public AvroFingerprint(byte[] value) {
			this.value = Require.nonNull(value);
		}

		@Override
		public void serialize(ByteDataOutput output) {
			output.writeByteArray(value);
		}

		@Override
		protected Object value() {
			return value;
		}
	}

	private final FingerprintAlgorithm algorithm;
	private final ReflectData data;
	private final BiFunction<org.apache.avro.Schema, OutputStream, ? extends Encoder> encoderFactory;
	private final BiFunction<org.apache.avro.Schema, InputStream, ? extends Decoder> decoderFactory;

	public AvroSchemaFactory() {
		this(FingerprintAlgorithm.CRC_64_AVRO, ReflectData.get(), EncoderFactory.get()::jsonEncoder, DecoderFactory.get()::jsonDecoder);
	}

	public AvroSchemaFactory(FingerprintAlgorithm algorithm, ReflectData data,
			TBiFunction<org.apache.avro.Schema, OutputStream, ? extends Encoder> encoderFactory,
			TBiFunction<org.apache.avro.Schema, InputStream, ? extends Decoder> decoderFactory) {
		this.algorithm = Require.nonNull(algorithm);
		this.data = Require.nonNull(data);
		this.encoderFactory = Require.nonNull(encoderFactory)::apply;
		this.decoderFactory = Require.nonNull(decoderFactory)::apply;
	}

	@Override
	public <T> Schema<T> createSchema(Class<T> type) {
		return new AvroSchema<>(this, type, data.getSchema(type));
	}

	@Override
	public Fingerprint readFingerprint(ByteDataInput input) {
		return AvroFingerprint.deserialize(input);
	}

	@Override
	public Schema<?> readSchema(ByteDataInput input) {
		return AvroSchema.deserialize(this, input);
	}
}
