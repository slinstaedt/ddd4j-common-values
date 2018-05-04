package org.ddd4j.schema.avro.conversion;

import java.util.function.Function;

import org.apache.avro.Conversion;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.ddd4j.util.Require;

public class StringConversion<T> extends Conversion<T> {

	public static <T> Conversion<T> of(Class<T> convertedType, Function<T, ? extends CharSequence> serializer,
			Function<CharSequence, T> deserializer) {
		return new StringConversion<>(convertedType, convertedType.getSimpleName(), serializer, deserializer);
	}

	public static <T> Conversion<T> ofString(Class<T> convertedType, Function<T, String> serializer, Function<String, T> deserializer) {
		return of(convertedType, serializer, deserializer.compose(CharSequence::toString));
	}

	public static <T> Conversion<T> ofString(Class<T> convertedType, String logicalTypeName, Function<T, String> serializer,
			Function<String, T> deserializer) {
		return new StringConversion<>(convertedType, logicalTypeName, serializer, deserializer.compose(CharSequence::toString));
	}

	private final Class<T> convertedType;
	private final String logicalTypeName;
	private final Function<T, ? extends CharSequence> serializer;
	private final Function<CharSequence, T> deserializer;

	public StringConversion(Class<T> convertedType, String logicalTypeName, Function<T, ? extends CharSequence> serializer,
			Function<CharSequence, T> deserializer) {
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
		return logicalType.addToSchema(Schema.create(Schema.Type.STRING));
	}

	@Override
	public T fromCharSequence(CharSequence value, Schema schema, LogicalType type) {
		return deserializer.apply(value);
	}

	@Override
	public CharSequence toCharSequence(T value, Schema schema, LogicalType type) {
		return serializer.apply(value);
	}
}