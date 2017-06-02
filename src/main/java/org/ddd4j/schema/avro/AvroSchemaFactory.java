package org.ddd4j.schema.avro;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.avro.Conversions;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectData;
import org.ddd4j.Throwing;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Key;
import org.ddd4j.value.collection.Configuration;

public class AvroSchemaFactory implements SchemaFactory {

	private static final Key<ReflectData> AVRO_DATA = Key.of(ReflectData.class, c -> new ReflectData());
	private static final Key<DecoderFactory> DECODER_FACTORY = Key.of(DecoderFactory.class, c -> DecoderFactory.get());
	private static final Key<EncoderFactory> ENCODER_FACTORY = Key.of(EncoderFactory.class, c -> EncoderFactory.get());

	private final Configuration configuration;
	private final DecoderFactory decoderFactory;
	private final EncoderFactory encoderFactory;
	private final ReflectData data;

	public AvroSchemaFactory(Context context) {
		this.configuration = context.configuration();
		this.decoderFactory = context.get(DECODER_FACTORY);
		this.encoderFactory = context.get(ENCODER_FACTORY);
		this.data = context.get(AVRO_DATA);
		data.addLogicalTypeConversion(new Conversions.UUIDConversion());
	}

	@Override
	public <T> Schema<T> createSchema(Class<T> type) {
		return new AvroSchema<>(this, data.getSchema(type));
	}

	@Override
	public Schema<?> readSchema(ReadBuffer buffer) throws IOException {
		return AvroSchema.deserialize(this, buffer);
	}

	ReflectData getData() {
		return data;
	}

	AvroFingerprintAlgorithm getFingerprintAlgorithm() {
		return configuration.getEnum(AvroFingerprintAlgorithm.class, "fingerprint").orElse(AvroFingerprintAlgorithm.SHA_256);
	}

	private AvroCoder coder() {
		return configuration.getEnum(AvroCoder.class, "coding").orElse(AvroCoder.JSON);
	}

	Decoder createDecoder(org.apache.avro.Schema schema, InputStream in) {
		try {
			return coder().decoder(decoderFactory, schema, in);
		} catch (IOException e) {
			return Throwing.unchecked(e);
		}
	}

	Encoder createEncoder(org.apache.avro.Schema schema, OutputStream out) {
		try {
			return coder().encoder(encoderFactory, schema, out);
		} catch (IOException e) {
			return Throwing.unchecked(e);
		}
	}
}
