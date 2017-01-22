package org.ddd4j.schema;

import java.io.IOException;
import java.util.Arrays;

import org.ddd4j.contract.Require;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.value.Value;

public class Fingerprint extends Value.Simple<Fingerprint, byte[]> {

	public static Fingerprint copied(byte[] value) {
		return new Fingerprint(Arrays.copyOf(value, value.length));
	}

	static Fingerprint deserialize(ReadBuffer buffer) throws IOException {
		return new Fingerprint(buffer.getBytes());
	}

	private final byte[] value;

	public Fingerprint(byte[] value) {
		this.value = Require.nonNull(value);
		Require.that(value, b -> b.length <= 256);
	}

	@Override
	public void serialize(WriteBuffer buffer) {
		buffer.putBytes(value);
	}

	@Override
	protected byte[] value() {
		return value;
	}
}
