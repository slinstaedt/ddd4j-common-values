package org.ddd4j.schema.avro.conversion;

import java.util.UUID;

import org.apache.avro.Conversion;

public class UUIDConversions {

	public static final Conversion<UUID> BYTES = FixedConversion.of(UUID.class, 16,
			(b, v) -> b.putLong(v.getMostSignificantBits()).putLong(v.getLeastSignificantBits()), b -> new UUID(b.getLong(), b.getLong()));

	public static final Conversion<UUID> STRING = StringConversion.ofString(UUID.class, UUID::toString, UUID::fromString);

	private UUIDConversions() {
	}
}