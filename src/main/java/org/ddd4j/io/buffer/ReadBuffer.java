package org.ddd4j.io.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import org.ddd4j.contract.Require;

public interface ReadBuffer extends RelativeBuffer {

	InputStream asInputStream();

	ReadBuffer duplicate();

	default byte get() {
		return bytes().get(advancePosition(Byte.BYTES));
	}

	default ReadBuffer get(byte[] dst) {
		return get(dst, 0, dst.length);
	}

	default ReadBuffer get(byte[] dst, int offset, int length) {
		Require.that(offset + length <= dst.length);
		bytes().get(advancePosition(length), dst, offset, length);
		return this;
	}

	default ReadBuffer get(ByteBuffer dst) {
		advancePosition(bytes().get(position(), remaining(), dst));
		return this;
	}

	default char getChar() {
		return bytes().getChar(advancePosition(Character.BYTES));
	}

	default double getDouble() {
		return bytes().getDouble(advancePosition(Double.BYTES));
	}

	default float getFloat() {
		return bytes().getFloat(advancePosition(Float.BYTES));
	}

	default int getInt() {
		return bytes().getInt(advancePosition(Integer.BYTES));
	}

	default long getLong() {
		return bytes().getLong(advancePosition(Long.BYTES));
	}

	default short getShort() {
		return bytes().getShort(advancePosition(Short.BYTES));
	}

	default int getUnsignedByte() {
		return bytes().getUnsignedByte(advancePosition(Byte.BYTES));
	}

	default int getUnsignedShort() {
		return bytes().getUnsignedShort(advancePosition(Short.BYTES));
	}

	default String getUTF() {
		return getUTFAsBuilder().toString();
	}

	default StringBuilder getUTFAsBuilder() {
		StringBuilder builder = bytes().getUTFAsBuilder(position());
		advancePosition(Bytes.utfLength(builder));
		return builder;
	}

	@Override
	ReadBuffer limit(int newLimit);

	@Override
	ReadBuffer mark();

	@Override
	ReadBuffer order(ByteOrder order);

	@Override
	ReadBuffer position(int newPosition);

	@Override
	ReadBuffer reset();

	ReadBuffer rewind();

	default ReadBuffer writeTo(WritableByteChannel channel) throws IOException {
		ByteBuffer buf = ByteBuffer.allocate(1024);
		while (hasRemaining()) {
			get(buf);
			buf.flip();
			while (buf.hasRemaining()) {
				channel.write(buf);
			}
			buf.clear();
		}
		return this;
	}
}