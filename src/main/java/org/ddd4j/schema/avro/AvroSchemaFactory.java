package org.ddd4j.schema.avro;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectData;
import org.ddd4j.Throwing;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.schema.avro.conversion.UUIDConversions;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Key;
import org.ddd4j.util.Type;
import org.ddd4j.value.config.ConfKey;
import org.ddd4j.value.config.Configuration;

public class AvroSchemaFactory implements SchemaFactory {

	public static final Key<ReflectData> AVRO_DATA = Key.of(ReflectData.class, c -> new ReflectData());
	public static final Key<DecoderFactory> DECODER_FACTORY = Key.of(DecoderFactory.class, c -> DecoderFactory.get());
	public static final Key<EncoderFactory> ENCODER_FACTORY = Key.of(EncoderFactory.class, c -> EncoderFactory.get());

	public static final ConfKey<AvroCoder> CODING = ConfKey.ofEnum(AvroCoder.class, "coding", AvroCoder.BINARY);
	public static final ConfKey<AvroFingerprintAlgorithm> FINGERPRINT = ConfKey.ofEnum(AvroFingerprintAlgorithm.class,
			"fingerprint", AvroFingerprintAlgorithm.SHA_256);

	private final Configuration configuration;
	private final DecoderFactory decoderFactory;
	private final EncoderFactory encoderFactory;
	private final ReflectData data;
	private final AvroCoder coder;

	public AvroSchemaFactory(Context context) {
		this.configuration = context.configuration();
		this.decoderFactory = context.get(DECODER_FACTORY);
		this.encoderFactory = context.get(ENCODER_FACTORY);
		this.data = context.get(AVRO_DATA);
		this.coder = context.conf(CODING);
		if (coder.isBinary()) {
			data.addLogicalTypeConversion(UUIDConversions.BYTES);
			data.addLogicalTypeConversion(UUIDConversions.STRING);
		} else {
			data.addLogicalTypeConversion(UUIDConversions.STRING);
			data.addLogicalTypeConversion(UUIDConversions.BYTES);
		}
	}

	@Override
	public <T> Schema<T> createSchema(Type<T> type) {
		return new AvroSchema<>(this, coder, getData().getSchema(type.getRawType()));
	}

	@Override
	public Schema<?> readSchema(ReadBuffer buffer) {
		AvroCoder writerCoder = AvroCoder.values()[buffer.getInt()];
		org.apache.avro.Schema writerSchema = new Parser().parse(buffer.getUTF());
		Class<?> type = getData().getClass(writerSchema);
		if (type == null || type == Object.class) {
			type = SchemaFactory.classForName(writerSchema.getFullName(), e -> Record.class);
		}
		return new AvroSchema<>(this, writerCoder, writerSchema);
	}

	ReflectData getData() {
		return data;
	}

	AvroFingerprintAlgorithm getFingerprintAlgorithm() {
		return configuration.get(FINGERPRINT);
	}

	@Override
	public String getName() {
		return "avro";
	}

	Decoder createDecoder(AvroCoder coder, org.apache.avro.Schema schema, InputStream in) {
		try {
			return coder.decoder(decoderFactory, schema, in);
		} catch (IOException e) {
			return Throwing.unchecked(e);
		}
	}

	Encoder createEncoder(org.apache.avro.Schema schema, OutputStream out) {
		try {
			return coder.encoder(encoderFactory, schema, out);
		} catch (IOException e) {
			return Throwing.unchecked(e);
		}
	}
}
