package org.ddd4j.value;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.ddd4j.Require;
import org.ddd4j.io.Bytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;

public class Hash<T> extends Value.Simple<Hash<T>, byte[]> {

	public static <T> Hash<T> copied(byte[] value) {
		return new Hash<>(Arrays.copyOf(value, value.length));
	}

	public static <T> Hash<T> deserialize(ReadBuffer buffer) {
		return new Hash<>(buffer.getBytes());
	}

	public static <T> Hash<T> of(int value) {
		return new Hash<>(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
	}

	public static <T> Hash<T> of(long value) {
		return new Hash<>(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
	}

	private final byte[] value;

	public Hash(byte[] value) {
		Require.that(value, b -> b.length <= 256);
		this.value = Require.nonNull(value);
	}

	@Override
	public void serialize(WriteBuffer buffer) {
		buffer.putBytes(value);
	}

	@Override
	protected byte[] value() {
		return value;
	}

	public ReadBuffer asBuffer() {
		return Bytes.wrap(value).readOnly().buffered();
	}
}
