package org.ddd4j.schema.avro.conversion;

import java.nio.ByteBuffer;
import java.util.UUID;

import org.apache.avro.Conversion;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericFixed;
import org.ddd4j.io.Bytes;

public class UUIDByteConversion extends Conversion<UUID> {

	private static final String NAME = "uuid";
	private static final int SIZE = 16;

	@Override
	public Class<UUID> getConvertedType() {
		return UUID.class;
	}

	@Override
	public Schema getRecommendedSchema() {
		return LogicalTypes.uuid().addToSchema(Schema.createFixed(NAME, "Byte conversion for UUID", null, SIZE));
	}

	@Override
	public String getLogicalTypeName() {
		return NAME;
	}

	@Override
	public UUID fromFixed(GenericFixed value, Schema schema, LogicalType type) {
		ByteBuffer buffer = ByteBuffer.wrap(value.bytes());
		return new UUID(buffer.getLong(), buffer.getLong());
	}

	@Override
	public GenericFixed toFixed(UUID value, Schema schema, LogicalType type) {
		byte[] bytes = new byte[schema.getFixedSize()];
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		Bytes.wrap(bytes);
		buffer.putLong(value.getMostSignificantBits());
		buffer.putLong(value.getLeastSignificantBits());
		return new GenericData.Fixed(schema, bytes);
	}
}