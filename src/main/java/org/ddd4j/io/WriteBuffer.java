package org.ddd4j.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.ddd4j.Require;
import org.ddd4j.Throwing.TConsumer;
import org.ddd4j.io.Bytes.Arrayed;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Key;

public interface WriteBuffer extends RelativeBuffer {

	class Pool {

		private final Supplier<PooledBytes<Arrayed>> factory;

		public Pool(Context context) {
			this.factory = context.get(PooledBytes.FACTORY);
		}

		public WriteBuffer get() {
			return factory.get().buffered();
		}

		public ReadBuffer serialized(Consumer<WriteBuffer> serializer) {
			WriteBuffer buffer = get();
			try {
				serializer.accept(buffer);
				return buffer.flip();
			} catch (Exception e) {
				buffer.close();
				throw e;
			}
		}
	}

	Key<WriteBuffer.Pool> POOL = Key.of(Pool.class, Pool::new);

	default WriteBuffer accept(Consumer<? super WriteBuffer> visitor) {
		visitor.accept(this);
		return this;
	}

	OutputStream asOutputStream();

	WriteBuffer duplicate();

	ReadBuffer flip();

	@Override
	WriteBuffer limit(int newLimit);

	@Override
	WriteBuffer mark();

	@Override
	WriteBuffer order(ByteOrder order);

	@Override
	int position();

	@Override
	WriteBuffer position(int newPosition);

	default WriteBuffer put(byte b) {
		backing().put(advancePosition(Byte.BYTES), b);
		return this;
	}

	default WriteBuffer put(byte[] src) {
		return put(src, 0, src.length);
	}

	default WriteBuffer put(byte[] src, int offset, int length) {
		Require.that(offset + length <= src.length);
		backing().put(advancePosition(length), src, offset, length);
		return this;
	}

	default WriteBuffer put(ByteBuffer src) {
		advancePosition(backing().put(position(), remaining(), src));
		return this;
	}

	default WriteBuffer put(ReadBuffer src) {
		advancePosition(backing().put(position(), remaining(), src));
		return this;
	}

	default WriteBuffer putBytes(byte[] bytes) {
		return putInt(bytes.length).put(bytes);
	}

	default WriteBuffer putChar(char value) {
		backing().putChar(advancePosition(Character.BYTES), value);
		return this;
	}

	default WriteBuffer putDouble(double value) {
		backing().putDouble(advancePosition(Double.BYTES), value);
		return this;
	}

	default WriteBuffer putFloat(float value) {
		backing().putFloat(advancePosition(Float.BYTES), value);
		return this;
	}

	default WriteBuffer putInt(int value) {
		backing().putInt(advancePosition(Integer.BYTES), value);
		return this;
	}

	default WriteBuffer putLong(long value) {
		backing().putLong(advancePosition(Long.BYTES), value);
		return this;
	}

	default WriteBuffer putShort(short value) {
		backing().putShort(advancePosition(Short.BYTES), value);
		return this;
	}

	default WriteBuffer putUnsignedByte(int value) {
		return put((byte) value);
	}

	default WriteBuffer putUnsignedShort(int value) {
		return putShort((short) value);
	}

	default WriteBuffer putUTF(CharSequence chars) {
		backing().putUTF(advancePosition(Bytes.utfLength(chars)), chars);
		return this;
	}

	default WriteBuffer readFrom(ReadableByteChannel channel) throws IOException {
		advancePosition(backing().readFrom(position(), remaining(), channel));
		return this;
	}

	@Override
	WriteBuffer reset();

	default void write(TConsumer<? super OutputStream> writer) {
		writer.accept(asOutputStream());
	}
}