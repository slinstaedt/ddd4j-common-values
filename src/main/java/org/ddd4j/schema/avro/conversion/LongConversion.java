package org.ddd4j.schema.avro.conversion;

import java.util.function.Function;

import org.apache.avro.Conversion;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.ddd4j.util.Require;

public class LongConversion<T> extends Conversion<T> {

	public static <T> Conversion<T> of(Class<T> convertedType, Function<T, Long> serializer, Function<Long, T> deserializer) {
		return new LongConversion<>(convertedType, convertedType.getSimpleName(), serializer, deserializer);
	}

	private final Class<T> convertedType;
	private final String logicalTypeName;
	private final Function<T, Long> serializer;
	private final Function<Long, T> deserializer;

	public LongConversion(Class<T> convertedType, String logicalTypeName, Function<T, Long> serializer, Function<Long, T> deserializer) {
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
		return logicalType.addToSchema(Schema.create(Schema.Type.LONG));
	}

	@Override
	public T fromLong(Long value, Schema schema, LogicalType type) {
		return deserializer.apply(value);
	}

	@Override
	public Long toLong(T value, Schema schema, LogicalType type) {
		return serializer.apply(value);
	}
}