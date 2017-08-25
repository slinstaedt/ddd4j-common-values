package org.ddd4j.schema.avro.conversion;

import java.util.function.Function;

import org.apache.avro.Conversion;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.ddd4j.Require;

public class BooleanConversion<T> extends Conversion<T> {

	public static <T> Conversion<T> of(Class<T> convertedType, Function<T, Boolean> serializer, Function<Boolean, T> deserializer) {
		return new BooleanConversion<>(convertedType, convertedType.getSimpleName(), serializer, deserializer);
	}

	private final Class<T> convertedType;
	private final String logicalTypeName;
	private final Function<T, Boolean> serializer;
	private final Function<Boolean, T> deserializer;

	public BooleanConversion(Class<T> convertedType, String logicalTypeName, Function<T, Boolean> serializer,
			Function<Boolean, T> deserializer) {
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
		return logicalType.addToSchema(Schema.create(Schema.Type.BOOLEAN));
	}

	@Override
	public T fromBoolean(Boolean value, Schema schema, LogicalType type) {
		return deserializer.apply(value);
	}

	@Override
	public Boolean toBoolean(T value, Schema schema, LogicalType type) {
		return serializer.apply(value);
	}
}