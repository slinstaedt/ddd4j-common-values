package org.ddd4j.schema;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.ddd4j.io.Bytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.util.value.Value;

public class Fingerprint extends Value.Simple<Fingerprint, byte[]> {

	public static Fingerprint copied(byte[] value) {
		return new Fingerprint(Arrays.copyOf(value, value.length));
	}

	public static Fingerprint deserialize(ReadBuffer buffer) {
		return new Fingerprint(buffer.getBytes());
	}

	public static Fingerprint of(int value) {
		return new Fingerprint(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
	}

	public static Fingerprint of(long value) {
		return new Fingerprint(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
	}

	private final byte[] hash;

	public Fingerprint(byte[] hash) {
		this.hash = Require.that(hash, b -> b != null && b.length <= 256);
	}

	public ReadBuffer asBuffer() {
		return Bytes.wrap(hash).readOnly().buffered();
	}

	@Override
	public void serialize(WriteBuffer buffer) {
		buffer.putBytes(hash);
	}

	@Override
	protected byte[] value() {
		return hash;
	}
}
