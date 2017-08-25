package org.ddd4j.schema.avro.conversion;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.apache.avro.Conversion;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericFixed;
import org.ddd4j.Require;

public class FixedConversion<T> extends Conversion<T> {

	public static <T> Conversion<T> of(Class<T> convertedType, int size, BiConsumer<ByteBuffer, T> serializer,
			Function<ByteBuffer, T> deserializer) {
		return new FixedConversion<>(convertedType, convertedType.getSimpleName(), size, serializer, deserializer);
	}

	private final Class<T> convertedType;
	private final String logicalTypeName;
	private final int size;
	private final BiConsumer<ByteBuffer, T> serializer;
	private final Function<ByteBuffer, T> deserializer;

	public FixedConversion(Class<T> convertedType, String logicalTypeName, int size, BiConsumer<ByteBuffer, T> serializer,
			Function<ByteBuffer, T> deserializer) {
		this.convertedType = Require.nonNull(convertedType);
		this.logicalTypeName = Require.nonEmpty(logicalTypeName);
		this.size = Require.that(size, size > 0);
		this.serializer = Require.nonNull(serializer);
		this.deserializer = Require.nonNull(deserializer);
	}

	@Override
	public Class<T> getConvertedType() {
		return convertedType;
	}

	@Override
	public String getLogicalTypeName() {
		return logicalTypeName;
	}

	@Override
	public Schema getRecommendedSchema() {
		LogicalType logicalType = new LogicalType(logicalTypeName);
		Schema schema = Schema.createFixed(logicalTypeName, "Byte conversion for " + logicalTypeName, null, size);
		return logicalType.addToSchema(schema);
	}

	@Override
	public T fromFixed(GenericFixed value, Schema schema, LogicalType type) {
		return deserializer.apply(ByteBuffer.wrap(value.bytes()));
	}

	@Override
	public GenericFixed toFixed(T value, Schema schema, LogicalType type) {
		byte[] bytes = new byte[schema.getFixedSize()];
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		serializer.accept(buffer, value);
		return new GenericData.Fixed(schema, bytes);
	}
}