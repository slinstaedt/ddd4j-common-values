package org.ddd4j.schema.avro.conversion;

import java.util.function.Function;

import org.apache.avro.Conversion;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData.Fixed;
import org.ddd4j.util.Require;
import org.apache.avro.generic.GenericFixed;

public class ByteConversion<T> extends Conversion<T> {

	public static <T> Conversion<T> of(Class<T> convertedType, Function<T, Byte> serializer, Function<Byte, T> deserializer) {
		return new ByteConversion<>(convertedType, convertedType.getSimpleName(), serializer, deserializer);
	}

	public static <T> Conversion<T> ofInt(Class<T> convertedType, Function<T, Integer> serializer, Function<Integer, T> deserializer) {
		return of(convertedType, serializer.andThen(Integer::byteValue), deserializer.compose(Byte::intValue));
	}

	private final Class<T> convertedType;
	private final String logicalTypeName;
	private final Function<T, Byte> serializer;
	private final Function<Byte, T> deserializer;

	public ByteConversion(Class<T> convertedType, String logicalTypeName, Function<T, Byte> serializer, Function<Byte, T> deserializer) {
		this.convertedType = Require.nonNull(convertedType);
		this.logicalTypeName = Require.nonEmpty(logicalTypeName);
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
		return logicalType.addToSchema(Schema.createFixed(logicalTypeName, "Byte conversion", null, 1));
	}

	@Override
	public T fromFixed(GenericFixed value, Schema schema, LogicalType type) {
		return deserializer.apply(value.bytes()[0]);
	}

	@Override
	public GenericFixed toFixed(T value, Schema schema, LogicalType type) {
		byte[] b = new byte[] { serializer.apply(value) };
		return new Fixed(schema, b);
	}
}