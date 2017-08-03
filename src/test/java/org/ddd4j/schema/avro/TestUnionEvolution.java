package org.ddd4j.schema.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.avro.Conversions;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.SchemaValidationException;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.avro.reflect.Union;
import org.ddd4j.Throwing;
import org.ddd4j.schema.avro.TestValue.X1;
import org.ddd4j.schema.avro.TestValue.X2;
import org.ddd4j.schema.avro.TestValue.X3;
import org.ddd4j.schema.avro.conversion.UUIDByteConversion;

public class TestUnionEvolution {

	public static final Schema READER_SCHEMA;

	static {
		ReflectData.get().addLogicalTypeConversion(new Conversions.UUIDConversion());
		ReflectData.get().addLogicalTypeConversion(new UUIDByteConversion());

		Schema schema = Throwing.task(() -> new Schema.Parser().parse(TestUnionEvolution.class.getResourceAsStream("X.json"))).get();
		List<Schema> types = new ArrayList<>(schema.getTypes());
		List<Field> fields = Collections.singletonList(new Schema.Field("value", Schema.create(Schema.Type.INT), null, (Object) null));
		fields = Collections.emptyList();
		types.add(Schema.createRecord("X3", null, types.get(0).getNamespace(), false, fields));
		READER_SCHEMA = Schema.createUnion(types);
	}

	public static final Schema WRITER_SCHEMA = ReflectData.get().getSchema(TestValue.class);

	public static byte[] encode(Object datum) throws IOException {
		try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
			Encoder encoder = EncoderFactory.get().jsonEncoder(WRITER_SCHEMA, stream);
			DatumWriter<Object> writer = new ReflectDatumWriter<>(WRITER_SCHEMA);
			writer.write(datum, encoder);
			encoder.flush();
			return stream.toByteArray();
		}
	}

	public static <T> T decode(byte[] data) throws IOException {
		try (ByteArrayInputStream stream = new ByteArrayInputStream(data)) {
			Decoder decoder = DecoderFactory.get().jsonDecoder(READER_SCHEMA, stream);
			DatumReader<T> reader = new GenericDatumReader<>(WRITER_SCHEMA, READER_SCHEMA);
			return reader.read(null, decoder);
		}
	}

	public static void main(String[] args) throws IOException, SchemaValidationException {
		System.out.println(WRITER_SCHEMA);
		System.out.println(READER_SCHEMA);

		// Object record = new GenericRecordBuilder(WRITER_SCHEMA).set("value", UUID.randomUUID()).build();
		Object record = new X3(42);
		byte[] encoded = encode(record);
		System.out.println(new String(encoded, "UTF-8"));

		Object decoded = decode(encoded);
		System.out.println(decoded.getClass());
		System.out.println(decoded);
	}
}

@Union({ X1.class, X2.class, X3.class })
interface TestValue {

	class X1 implements TestValue {

		private final Double value;

		protected X1() {
			value = null;
		}

		public X1(Double value) {
			this.value = value;
		}

		@Override
		public Object value() {
			return value;
		}
	}

	class X2 implements TestValue {

		private final UUID value;

		protected X2() {
			value = null;
		}

		public X2(UUID value) {
			this.value = value;
		}

		@Override
		public Object value() {
			return value;
		}
	}

	class X3 implements TestValue {

		private final Integer value;

		protected X3() {
			value = null;
		}

		public X3(Integer value) {
			this.value = value;
		}

		@Override
		public Object value() {
			return value;
		}
	}

	Object value();
}
