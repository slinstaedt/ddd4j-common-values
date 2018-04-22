package org.ddd4j.value.behavior;

import java.util.UUID;

import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.value.Value;

public class Identifier extends Value.Simple<Identifier, UUID> {

	private final UUID value;

	public Identifier() {
		this.value = UUID.randomUUID();
	}

	public Identifier(long mostSigBits, long leastSigBits) {
		this.value = new UUID(mostSigBits, leastSigBits);
	}

	public Identifier(ReadBuffer buffer) {
		this(buffer.getLong(), buffer.getLong());
	}

	@Override
	public void serialize(WriteBuffer buffer) {
		buffer.putLong(value.getMostSignificantBits());
		buffer.putLong(value.getLeastSignificantBits());
	}

	@Override
	protected UUID value() {
		return value;
	}
}
