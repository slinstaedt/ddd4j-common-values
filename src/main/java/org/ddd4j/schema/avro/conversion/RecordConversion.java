package org.ddd4j.schema.avro.conversion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.avro.Conversion;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.avro.generic.IndexedRecord;
import org.ddd4j.Require;

public interface RecordConversion<T, P> {

	class Chain<T, P, F, X> implements RecordConversion<T, X> {

		private final RecordConversion<T, P> parent;
		private final String name;
		private final Conversion<F> conversion;
		private final Schema schema;
		private final LogicalType type;
		private final Function<T, F> accessor;
		private final BiFunction<P, F, X> mapper;

		public Chain(RecordConversion<T, P> parent, String name, Conversion<F> conversion, Schema schema, LogicalType type,
				Function<T, F> accessor, BiFunction<P, F, X> mapper) {
			this.parent = Require.nonNull(parent);
			this.name = Require.nonEmpty(name);
			this.conversion = Require.nonNull(conversion);
			this.schema = Require.nonNull(schema);
			this.type = Require.nonNull(type);
			this.accessor = Require.nonNull(accessor);
			this.mapper = Require.nonNull(mapper);
		}

		@Override
		public X fromRecord(Function<String, Object> record) {
			Object rawValue = record.apply(name);
			Object logicalValue = Conversions.convertToLogicalType(rawValue, schema, type, conversion);
			F fieldValue = conversion.getConvertedType().cast(logicalValue);
			P parentValue = parent.fromRecord(record);
			return mapper.apply(parentValue, fieldValue);
		}

		@Override
		public Class<T> getConvertedType() {
			return parent.getConvertedType();
		}

		@Override
		public String getLogicalTypeName() {
			return parent.getLogicalTypeName();
		}

		@Override
		public Schema recommendedSchema(List<Field> fields) {
			fields.add(new Schema.Field(name, schema, "Field " + name + " for " + getLogicalTypeName(), (Object) null));
			return parent.recommendedSchema(fields);
		}

		@Override
		public IndexedRecord toRecord(GenericRecordBuilder builder, T value) {
			F fieldValue = accessor.apply(value);
			Object rawValue = Conversions.convertToRawType(fieldValue, schema, type, conversion);
			builder.set(name, rawValue);
			return parent.toRecord(builder, value);
		}
	}

	class Root<T> implements RecordConversion<T, Void> {

		private final Class<T> convertedType;
		private final String logicalTypeName;

		public Root(Class<T> convertedType, String logicalTypeName) {
			this.convertedType = Require.nonNull(convertedType);
			this.logicalTypeName = Require.nonEmpty(logicalTypeName);
		}

		@Override
		public Void fromRecord(Function<String, Object> record) {
			return null;
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
		public Schema recommendedSchema(List<Field> fields) {
			LogicalType logicalType = new LogicalType(logicalTypeName);
			Schema schema = Schema.createRecord(logicalTypeName, "Record type for " + logicalTypeName, null, false, fields);
			return logicalType.addToSchema(schema);
		}

		@Override
		public IndexedRecord toRecord(GenericRecordBuilder builder, T value) {
			return builder.build();
		}
	}

	class Terminal<T, P> extends Conversion<T> {

		private final RecordConversion<T, P> parent;
		private final Function<P, T> constructor;

		public Terminal(RecordConversion<T, P> parent, Function<P, T> constructor) {
			this.parent = Require.nonNull(parent);
			this.constructor = Require.nonNull(constructor);
		}

		@Override
		public T fromRecord(IndexedRecord value, Schema schema, LogicalType type) {
			P args = parent.fromRecord(name -> value.get(schema.getField(name).pos()));
			return constructor.apply(args);
		}

		@Override
		public Class<T> getConvertedType() {
			return parent.getConvertedType();
		}

		@Override
		public String getLogicalTypeName() {
			return parent.getLogicalTypeName();
		}

		@Override
		public Schema getRecommendedSchema() {
			return parent.recommendedSchema(new ArrayList<>());
		}

		@Override
		public IndexedRecord toRecord(T value, Schema schema, LogicalType type) {
			return parent.toRecord(new GenericRecordBuilder(schema), value);
		}
	}

	static <T> RecordConversion<T, Void> builder(Class<T> convertedType) {
		return builder(convertedType, convertedType.getSimpleName());
	}

	static <T> RecordConversion<T, Void> builder(Class<T> convertedType, String logicalTypeName) {
		return new Root<>(convertedType, logicalTypeName);
	}

	default Conversion<T> build(Function<P, T> constructor) {
		return new Terminal<>(this, constructor);
	}

	P fromRecord(Function<String, Object> record);

	Class<T> getConvertedType();

	String getLogicalTypeName();

	Schema recommendedSchema(List<Schema.Field> fields);

	IndexedRecord toRecord(GenericRecordBuilder builder, T value);

	default <F, X> RecordConversion<T, X> with(String field, Conversion<F> conversion, Function<T, F> accessor,
			BiFunction<P, F, X> mapper) {
		Schema schema = conversion.getRecommendedSchema();
		LogicalType type = new LogicalType(conversion.getLogicalTypeName());
		return with(field, conversion, schema, type, accessor, mapper);
	}

	default <F, X> RecordConversion<T, X> with(String field, Conversion<F> conversion, Schema schema, LogicalType type,
			Function<T, F> accessor, BiFunction<P, F, X> mapper) {
		return new Chain<>(this, field, conversion, schema, type, accessor, mapper);
	}
}