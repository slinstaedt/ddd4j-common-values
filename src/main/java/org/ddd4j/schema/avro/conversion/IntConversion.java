package org.ddd4j.schema.avro.conversion;

import java.util.function.Function;

import org.apache.avro.Conversion;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.ddd4j.util.Require;

public class IntConversion<T> extends Conversion<T> {

	public static <T> Conversion<T> of(Class<T> convertedType, Function<T, Integer> serializer, Function<Integer, T> deserializer) {
		return new IntConversion<>(convertedType, convertedType.getSimpleName(), serializer, deserializer);
	}

	private final Class<T> convertedType;
	private final String logicalTypeName;
	private final Function<T, Integer> serializer;
	private final Function<Integer, T> deserializer;

	public IntConversion(Class<T> convertedType, String logicalTypeName, Function<T, Integer> serializer,
			Function<Integer, T> deserializer) {
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
		return logicalType.addToSchema(Schema.create(Schema.Type.INT));
	}

	@Override
	public T fromInt(Integer value, Schema schema, LogicalType type) {
		return deserializer.apply(value);
	}

	@Override
	public Integer toInt(T value, Schema schema, LogicalType type) {
		return serializer.apply(value);
	}
}