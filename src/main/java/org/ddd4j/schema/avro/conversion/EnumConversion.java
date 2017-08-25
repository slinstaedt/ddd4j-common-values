package org.ddd4j.schema.avro.conversion;

import java.util.function.Function;

import org.apache.avro.Conversion;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData.EnumSymbol;
import org.apache.avro.generic.GenericEnumSymbol;
import org.ddd4j.Require;

public class EnumConversion<T extends Enum<T>> extends Conversion<T> {

	public static <T extends Enum<T>> Conversion<T> of(Class<T> convertedType) {
		return of(convertedType, Enum::name, name -> Enum.valueOf(convertedType, name));
	}

	public static <T extends Enum<T>> Conversion<T> of(Class<T> convertedType, Function<T, String> serializer,
			Function<String, T> deserializer) {
		return new EnumConversion<>(convertedType, convertedType.getSimpleName(), serializer, deserializer);
	}

	private final Class<T> convertedType;
	private final String logicalTypeName;
	private final Function<T, String> serializer;
	private final Function<String, T> deserializer;

	public EnumConversion(Class<T> convertedType, String logicalTypeName, Function<T, String> serializer,
			Function<String, T> deserializer) {
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
	public T fromEnumSymbol(GenericEnumSymbol value, Schema schema, LogicalType type) {
		return deserializer.apply(value.toString());
	}

	@Override
	public GenericEnumSymbol toEnumSymbol(T value, Schema schema, LogicalType type) {
		return new EnumSymbol(schema, serializer.apply(value));
	}
}