package org.ddd4j.schema;

import java.io.IOException;
import java.util.Arrays;

import org.ddd4j.contract.Require;
import org.ddd4j.io.Input;
import org.ddd4j.io.buffer.WriteBuffer;
import org.ddd4j.value.Value;

public class Fingerprint extends Value.Simple<Fingerprint, byte[]> {

	public static Fingerprint copied(byte[] value) {
		return new Fingerprint(Arrays.copyOf(value, value.length));
	}

	static Fingerprint deserialize(Input input) throws IOException {
		return new Fingerprint(input.readByteArray());
	}

	private final byte[] value;

	public Fingerprint(byte[] value) {
		this.value = Require.nonNull(value);
		Require.that(value, b -> b.length <= 256);
	}

	@Override
	public void serialize(WriteBuffer buffer) {
		buffer.writeByteArray(value);
	}

	@Override
	protected byte[] value() {
		return value;
	}
}
