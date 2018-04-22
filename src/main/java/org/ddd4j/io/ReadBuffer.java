package org.ddd4j.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.function.Consumer;

import org.ddd4j.Require;
import org.ddd4j.Throwing.TConsumer;

public interface ReadBuffer extends RelativeBuffer {

	default ReadBuffer accept(Consumer<? super ReadBuffer> visitor) {
		visitor.accept(this);
		return this;
	}

	default Bytes asBytes() {
		return backing().sliceBy(position(), remaining());
	}

	InputStream asInputStream();

	ReadBuffer duplicate();

	default <E extends Throwable> void forEachRemaining(ByteConsumer<E> consumer) throws E {
		backing().forEach(consumer, position(), remaining());
	}

	default byte get() {
		return backing().get(advancePosition(Byte.BYTES));
	}

	default ReadBuffer get(byte[] dst) {
		return get(dst, 0, dst.length);
	}

	default ReadBuffer get(byte[] dst, int offset, int length) {
		Require.that(offset + length <= dst.length);
		backing().get(advancePosition(length), dst, offset, length);
		return this;
	}

	default ReadBuffer get(ByteBuffer dst) {
		advancePosition(backing().get(position(), remaining(), dst));
		return this;
	}

	default ReadBuffer get(WriteBuffer dst) {
		advancePosition(backing().get(position(), remaining(), dst));
		return this;
	}

	default boolean getBoolean() {
		return backing().getBoolean(advancePosition(Byte.BYTES));
	}

	default byte[] getBytes() {
		byte[] bytes = new byte[getInt()];
		get(bytes);
		return bytes;
	}

	default char getChar() {
		return backing().getChar(advancePosition(Character.BYTES));
	}

	default double getDouble() {
		return backing().getDouble(advancePosition(Double.BYTES));
	}

	default float getFloat() {
		return backing().getFloat(advancePosition(Float.BYTES));
	}

	default int getInt() {
		return backing().getInt(advancePosition(Integer.BYTES));
	}

	default long getLong() {
		return backing().getLong(advancePosition(Long.BYTES));
	}

	default short getShort() {
		return backing().getShort(advancePosition(Short.BYTES));
	}

	default int getUnsignedByte() {
		return backing().getUnsignedByte(advancePosition(Byte.BYTES));
	}

	default int getUnsignedShort() {
		return backing().getUnsignedShort(advancePosition(Short.BYTES));
	}

	default String getUTF() {
		return getUTFAsBuilder().toString();
	}

	default StringBuilder getUTFAsBuilder() {
		StringBuilder builder = backing().getUTFAsBuilder(position());
		advancePosition(Bytes.utfLength(builder));
		return builder;
	}

	default int hash() {
		return backing().hash(position(), remaining());
	}

	@Override
	ReadBuffer limit(int newLimit);

	@Override
	ReadBuffer limitToRemaining(int remaining);

	@Override
	ReadBuffer mark();

	@Override
	ReadBuffer order(ByteOrder order);

	@Override
	ReadBuffer position(int newPosition);

	default void read(TConsumer<? super InputStream> reader) {
		reader.accept(asInputStream());
	}

	@Override
	ReadBuffer reset();

	ReadBuffer rewind();

	default byte[] toByteArray() {
		byte[] b = new byte[remaining()];
		backing().get(position(), b);
		return b;
	}

	default ReadBuffer writeTo(WritableByteChannel channel) throws IOException {
		advancePosition(backing().writeTo(position(), remaining(), channel));
		return this;
	}
}