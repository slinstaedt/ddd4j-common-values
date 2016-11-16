package org.ddd4j.schema.avro;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.function.BiFunction;

import org.apache.avro.Schema.Parser;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificData;
import org.ddd4j.contract.Require;
import org.ddd4j.io.ByteDataInput;
import org.ddd4j.io.ByteDataOutput;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.Schema.Fingerprint;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.Throwing.TBiFunction;

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

	class AvroSchema<T> implements Schema<T> {

		private final Class<T> type;
		private final org.apache.avro.Schema schema;

		public AvroSchema(Class<T> type, org.apache.avro.Schema schema) {
			this.type = Require.nonNull(type);
			this.schema = Require.nonNull(schema);
		}

		@Override
		public String getName() {
			return schema.getFullName();
		}

		@Override
		public Fingerprint getFingerprint() {
			return algorithm.parsingFingerprint(schema);
		}

		@Override
		public void serialize(ByteDataOutput output) {
			output.writeUTF(schema.toString());
		}

		@Override
		public int hash() {
			return schema.hashCode();
		}

		@Override
		public boolean equal(Schema<T> other) {
			return other.<AvroSchema>as(AvroSchema.class).mapNonNull(o -> o.schema).checkEqual(schema);
		}

		@Override
		public int hashCode(Object object) {
			return data.hashCode(object, schema);
		}

		@Override
		public Reader<T> createReader(ByteDataInput input) {
			Decoder decoder = decoderFactory.apply(schema, input.asStream());
			DatumReader<?> reader = data.createDatumReader(schema);
			return Throwing.ofSupplied(() -> reader.read(null, decoder)).map(type::cast)::get;
		}

		@Override
		public Writer<T> createWriter(ByteDataOutput output) {
			Encoder encoder = encoderFactory.apply(schema, output.asStream());
			@SuppressWarnings("unchecked")
			DatumWriter<Object> writer = data.createDatumWriter(schema);
			return Throwing.ofConsumed(t -> writer.write(t, encoder))::accept;
		}
	}

	static class AvroFingerprint implements Fingerprint {

		private final byte[] value;

		public AvroFingerprint(byte[] value) {
			this.value = Require.nonNull(value);
		}

		@Override
		public int hash() {
			return Arrays.hashCode(value);
		}

		@Override
		public boolean equal(Fingerprint other) {
			return other.as(AvroFingerprint.class).mapNonNull(o -> o.value).checkEqual(value);
		}

		@Override
		public void serialize(ByteDataOutput output) {
			output.writeByteArray(value);
		}
	}

	private final FingerprintAlgorithm algorithm;
	private final SpecificData data;
	private final BiFunction<org.apache.avro.Schema, OutputStream, ? extends Encoder> encoderFactory;
	private final BiFunction<org.apache.avro.Schema, InputStream, ? extends Decoder> decoderFactory;

	public AvroSchemaFactory() {
		this(FingerprintAlgorithm.CRC_64_AVRO, SpecificData.get(), EncoderFactory.get()::jsonEncoder, DecoderFactory.get()::jsonDecoder);
	}

	public AvroSchemaFactory(FingerprintAlgorithm algorithm, SpecificData data,
			TBiFunction<org.apache.avro.Schema, OutputStream, ? extends Encoder> encoderFactory,
			TBiFunction<org.apache.avro.Schema, InputStream, ? extends Decoder> decoderFactory) {
		this.algorithm = Require.nonNull(algorithm);
		this.data = Require.nonNull(data);
		this.encoderFactory = Require.nonNull(encoderFactory)::apply;
		this.decoderFactory = Require.nonNull(decoderFactory)::apply;
	}

	@Override
	public <T> Schema<T> createSchema(Class<T> type) {
		return new AvroSchema<>(type, data.getSchema(type));
	}

	@Override
	public Fingerprint readFingerprint(ByteDataInput input) {
		return new AvroFingerprint(input.readByteArray());
	}

	@Override
	public Schema<?> readSchema(ByteDataInput input) {
		org.apache.avro.Schema schema = new Parser().parse(input.readUTF());
		return new AvroSchema<>(SchemaFactory.classForName(schema.getFullName()), schema);
	}
}
