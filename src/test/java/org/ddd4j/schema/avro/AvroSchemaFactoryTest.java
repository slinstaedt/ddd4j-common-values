package org.ddd4j.schema.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.apache.avro.Conversions;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.AvroAlias;
import org.apache.avro.reflect.AvroDefault;
import org.apache.avro.reflect.AvroName;
import org.apache.avro.reflect.Nullable;
import org.apache.avro.reflect.ReflectData;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.avro.AvroSchemaFactory.FingerprintAlgorithm;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AvroSchemaFactoryTest {

	static final String SCHEMA2 = "A{\"type\":\"record\",\"name\":\"V2\",\"namespace\":\"org.ddd4j.schema.avro.AvroSchemaFactoryTest$\",\"fields\":[{\"name\":\"description\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"YYY\",\"fields\":[{\"name\":\"value\",\"type\":\"long\"},{\"name\":\"uuid\",\"type\":{\"type\":\"string\",\"logicalType\":\"uuid\"}}]}}]}";
	static final String SCHEMA3 = "A{\"type\":\"record\",\"name\":\"V3\",\"namespace\":\"org.ddd4j.schema.avro.AvroSchemaFactoryTest$\",\"fields\":[{\"name\":\"description\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"YYY\",\"fields\":[{\"name\":\"value\",\"type\":\"long\"},{\"name\":\"uuid\",\"type\":{\"type\":\"string\",\"logicalType\":\"uuid\"}}]}}]}";
	static final String TEST_DATA = "{\"description\":{\"string\":\"abc\"},\"value\":{\"value\":-4,\"uuid\":\"ae6aceea-ae7a-4abf-bea0-478f32188a26\"}}{\"description\":{\"string\":\"abc\"},\"value\":{\"value\":-4,\"uuid\":\"ae6aceea-ae7a-4abf-bea0-478f32188a26\"}}";

	public static class V1 {

		V1() {
		}

		public V1(String description) {
			this.description = description;
			this.value = new YYY();
		}

		@Nullable
		String description;

		YYY value;
	}

	@AvroAlias(alias = "V1")
	public static class V2 {

		@AvroName("value")
		YYY value2;
	}

	@AvroAlias(alias = "V1")
	public static class V3 {

		@Nullable
		String description;

		YYY value;

		@AvroDefault("-1")
		int x;
	}

	public static class YYY {

		long value = -4;

		UUID uuid = UUID.randomUUID();
	}

	private AvroSchemaFactory testUnit;
	private V1 testValue;

	@Before
	public void setup() {
		testValue = new V1("abc");
		ReflectData data = new ReflectData();
		data.addLogicalTypeConversion(new Conversions.UUIDConversion());
		testUnit = new AvroSchemaFactory(FingerprintAlgorithm.SHA_256, data, EncoderFactory.get()::jsonEncoder, DecoderFactory.get()::jsonDecoder);
	}

	@Test
	public void sameSchema() throws IOException {
		Schema<V1> schema = testUnit.createSchema(V1.class);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		schema.createWriter(baos::write).writeAndFlush(testValue);

		V1 copy = schema.createReader(new ByteArrayInputStream(baos.toByteArray())::read).read();
		Assert.assertNotNull(copy);
		Assert.assertTrue(schema.equal(testValue, copy));
	}

	@Test
	public void serializedSchema() throws IOException {
		Schema<V1> schema = testUnit.createSchema(V1.class);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		schema.serialize(baos::write);
		schema.createWriter(baos::write).writeAndFlush(testValue);

		ByteArrayInputStream input = new ByteArrayInputStream(baos.toByteArray());
		Schema<?> readSchema = testUnit.readSchema(input::read);
		Object copy = readSchema.createReader(input::read).read();

		Assert.assertNotNull(copy);
		Assert.assertTrue(schema.equal(testValue, copy));
	}

	@Test
	public void reducedSchema() throws IOException {
		Schema<?> schema = testUnit.readSchema(new ByteArrayInputStream(SCHEMA2.getBytes())::read);
		V2 copy = (V2) schema.createReader(new ByteArrayInputStream(TEST_DATA.getBytes())::read).read();

		Assert.assertNotNull(copy);
		Assert.assertFalse(testUnit.createSchema(V2.class).equal(testValue, copy));
	}

	@Test
	public void extendedSchema() throws IOException {
		Schema<?> schema = testUnit.readSchema(new ByteArrayInputStream(SCHEMA3.getBytes())::read);
		V3 copy = (V3) schema.createReader(new ByteArrayInputStream(TEST_DATA.getBytes())::read).read();

		Assert.assertNotNull(copy);
		Assert.assertFalse(testUnit.createSchema(V2.class).equal(testValue, copy));
		Assert.assertEquals(-1, copy.x);
	}

	@Test
	public void schemaCompability() {
		Schema<?> schema1 = testUnit.createSchema(V1.class);
		Schema<?> schema2 = testUnit.createSchema(V2.class);
		Schema<?> schema3 = testUnit.createSchema(V3.class);

		Assert.assertTrue(schema2.compatibleWith(schema1));
		Assert.assertTrue(schema3.compatibleWith(schema1));
		Assert.assertFalse(schema1.compatibleWith(schema2));
		Assert.assertFalse(schema3.compatibleWith(schema2));
		Assert.assertFalse(schema1.compatibleWith(schema3));
		Assert.assertFalse(schema2.compatibleWith(schema3));
	}
}
