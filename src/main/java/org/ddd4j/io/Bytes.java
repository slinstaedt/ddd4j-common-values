package org.ddd4j.io;

import java.io.IOException;
import java.io.UTFDataFormatException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.ddd4j.Throwing;
import org.ddd4j.collection.Cache;
import org.ddd4j.contract.Require;
import org.ddd4j.io.ByteOrder.IndexedBytes;

public abstract class Bytes implements IndexedBytes, AutoCloseable {

	private static final int HASH_SEED = 0x9747b28c;

	public static final Bytes NULL = new Bytes() {

		@Override
		public byte get(int index) {
			return 0;
		}

		@Override
		public int length() {
			return Integer.MAX_VALUE;
		}

		@Override
		public Bytes put(int index, byte b) {
			return this;
		}
	};

	static int utfLength(CharSequence chars) {
		int strlen = chars.length();
		int utflen = 0;
		for (int i = 0; i < strlen; i++) {
			int c = chars.charAt(i);
			if (c >= 0x0001 && c <= 0x007F) {
				utflen++;
			} else if (c > 0x07FF) {
				utflen += 3;
			} else {
				utflen += 2;
			}
		}
		return utflen;
	}

	public static Bytes wrap(byte[] bytes) {
		return wrap(bytes, 0, bytes.length);
	}

	public static Bytes wrap(byte[] bytes, int offset, int length) {
		Require.nonNull(bytes);
		return new Bytes() {

			@Override
			public byte get(int index) {
				return bytes[offset + index];
			}

			@Override
			public int length() {
				return length;
			}

			@Override
			public Bytes put(int index, byte b) {
				bytes[offset + index] = b;
				return this;
			}
		};
	}

	public static Bytes wrap(ByteBuffer buffer) {
		Require.nonNull(buffer);
		return new Bytes() {

			private final int position = buffer.position();
			private final int limit = buffer.limit();

			@Override
			public byte get(int index) {
				return buffer.get(position + index);
			}

			@Override
			public int get(int index, int amount, ByteBuffer dst) {
				buffer.position(position + index).limit(position + index + Math.min(dst.remaining(), Math.min(amount, length())));
				dst.put(buffer);
				buffer.limit(limit);
				return buffer.position() - index;
			}

			@Override
			public int length() {
				return limit - position;
			}

			@Override
			public Bytes put(int index, byte b) {
				buffer.put(position + index, b);
				return this;
			}

			@Override
			public int put(int index, int amount, ByteBuffer src) {
				buffer.position(position + index).limit(position + index + Math.min(amount, length()));
				int oldLimit = src.limit();
				src.limit(Math.min(src.remaining(), buffer.remaining()));
				buffer.put(src);
				src.limit(oldLimit);
				buffer.limit(limit);
				return buffer.position() - index;
			}

			@Override
			public int readFrom(int index, int amount, ReadableByteChannel channel) throws IOException {
				buffer.position(position + index).limit(position + index + amount);
				try {
					return channel.read(buffer);
				} finally {
					buffer.limit(limit);
				}
			}

			@Override
			public int writeTo(int index, int amount, WritableByteChannel channel) throws IOException {
				buffer.position(position + index).limit(position + index + amount);
				try {
					return channel.write(buffer);
				} finally {
					buffer.limit(limit);
				}
			}
		};
	}

	public static Bytes wrapReleasable(ByteBuffer buffer, Cache<?, ByteBuffer> cache) {
		Require.nonNullElements(buffer, cache);
		return new Bytes() {

			@Override
			public void close() {
				cache.release(buffer);
			}

			@Override
			public byte get(int index) {
				return buffer.get(index);
			}

			@Override
			public int length() {
				return buffer.capacity();
			}

			@Override
			public Bytes put(int index, byte b) {
				buffer.put(index, b);
				return this;
			}
		};
	}

	private ByteOrder order;

	public Bytes() {
		this.order = ByteOrder.BIG_ENDIAN;
	}

	public Buffer buffered() {
		return new Buffer(this);
	}

	@Override
	public void close() {
	}

	public Bytes congruent(Bytes other) {
		int i = 0;
		while (this.get(i) == other.get(i)) {
			i++;
		}
		return sliceBy(0, i);
	}

	@Override
	public abstract byte get(int index);

	public Bytes get(int index, byte[] dst) {
		return get(index, dst, 0, dst.length);
	}

	public Bytes get(int index, byte[] dst, int offset, int length) {
		for (int i = 0; i < length; i++) {
			dst[offset + i] = get(index + i);
		}
		return this;
	}

	public int get(int index, int amount, ByteBuffer dst) {
		amount = Math.min(amount, remaining(index));
		amount = Math.min(amount, dst.remaining());
		for (int i = 0; i < amount; i++) {
			dst.put(get(index++));
		}
		return amount;
	}

	public boolean getBoolean(int index) {
		return get(index) != 0;
	}

	public char getChar(int index) {
		return order.getChar(this, index);
	}

	public double getDouble(int index) {
		return order.getDouble(this, index);
	}

	public float getFloat(int index) {
		return order.getFloat(this, index);
	}

	public int getInt(int index) {
		return order.getInt(this, index);
	}

	public long getLong(int index) {
		return order.getLong(this, index);
	}

	public short getShort(int index) {
		return order.getShort(this, index);
	}

	public int getUnsignedByte(int index) {
		return Byte.toUnsignedInt(get(index));
	}

	public int getUnsignedShort(int index) {
		return Short.toUnsignedInt(getShort(index));
	}

	public String getUTF(int index) {
		return getUTFAsBuilder(index).toString();
	}

	public StringBuilder getUTFAsBuilder(int index) {
		int utflen = getUnsignedShort(index);
		StringBuilder builder = new StringBuilder(utflen);

		int c, char2, char3;
		int count = index;
		while (count < index + utflen) {
			c = get(count);
			switch (c >> 4) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
				/* 0xxxxxxx */
				count++;
				builder.append((char) c);
				break;
			case 12:
			case 13:
				/* 110x xxxx 10xx xxxx */
				count += 2;
				if (count > utflen) {
					return Throwing.unchecked(new UTFDataFormatException("malformed input: partial character at end"));
				}
				char2 = get(count - 1);
				if ((char2 & 0xC0) != 0x80) {
					return Throwing.unchecked(new UTFDataFormatException("malformed input around byte " + count));
				}
				builder.append((char) (((c & 0x1F) << 6) | (char2 & 0x3F)));
				break;
			case 14:
				/* 1110 xxxx 10xx xxxx 10xx xxxx */
				count += 3;
				if (count > utflen) {
					return Throwing.unchecked(new UTFDataFormatException("malformed input: partial character at end"));
				}
				char2 = get(count - 2);
				char3 = get(count - 1);
				if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) {
					return Throwing.unchecked(new UTFDataFormatException("malformed input around byte " + (count - 1)));
				}
				builder.append((char) (((c & 0x0F) << 12) | ((char2 & 0x3F) << 6) | ((char3 & 0x3F) << 0)));
				break;
			default:
				/* 10xx xxxx, 1111 xxxx */
				return Throwing.unchecked(new UTFDataFormatException("malformed input around byte " + count));
			}
		}
		return builder;
	}

	public int hash(int index, int length) {
		// 'm' and 'r' are mixing constants generated offline.
		// They're not really 'magic', they just happen to work well.
		final int m = 0x5bd1e995;
		final int r = 24;

		// Initialize the hash to a random value
		int h = HASH_SEED ^ length;

		for (int i = index; i < length - 3; i += Integer.BYTES) {
			int k = getInt(i);
			k *= m;
			k ^= k >>> r;
			k *= m;
			h *= m;
			h ^= k;
		}

		// Handle the last few bytes of the input array
		switch (length % 4) {
		case 3:
			h ^= (get((length & ~3) + 2) & 0xff) << 16;
		case 2:
			h ^= (get((length & ~3) + 1) & 0xff) << 8;
		case 1:
			h ^= get(length & ~3) & 0xff;
			h *= m;
		}

		h ^= h >>> 13;
		h *= m;
		h ^= h >>> 15;

		return h;
	}

	public abstract int length();

	public ByteOrder order() {
		return order;
	}

	public Bytes order(ByteOrder order) {
		this.order = Require.nonNull(order);
		return this;
	}

	@Override
	public abstract Bytes put(int index, byte b);

	public Bytes put(int index, byte[] src) {
		return put(index, src, 0, src.length);
	}

	public Bytes put(int index, byte[] src, int offset, int length) {
		for (int i = 0; i < length; i++) {
			put(index + i, src[offset + i]);
		}
		return this;
	}

	public int put(int index, int amount, ByteBuffer src) {
		amount = Math.min(amount, remaining(index));
		amount = Math.min(amount, src.remaining());
		for (int i = 0; i < amount; i++) {
			put(index++, src.get());
		}
		return amount;
	}

	public Bytes putBoolean(int index, boolean value) {
		return put(index, (byte) (value ? 1 : 0));
	}

	public Bytes putChar(int index, char value) {
		order.putChar(this, index, value);
		return this;
	}

	public Bytes putDouble(int index, double value) {
		order.putDouble(this, index, value);
		return this;
	}

	public Bytes putFloat(int index, float value) {
		order.putFloat(this, index, value);
		return this;
	}

	public Bytes putInt(int index, int value) {
		order.putInt(this, index, value);
		return this;
	}

	public Bytes putLong(int index, long value) {
		order.putLong(this, index, value);
		return this;
	}

	public Bytes putShort(int index, short value) {
		order.putShort(this, index, value);
		return this;
	}

	public Bytes putUnsignedByte(int index, int value) {
		put(index, (byte) value);
		return this;
	}

	public Bytes putUnsignedShort(int index, int value) {
		return putShort(index, (short) value);
	}

	public Bytes putUTF(int index, CharSequence chars) {
		int strlen = chars.length();
		int utflen = utfLength(chars);
		if (utflen > 65535) {
			return Throwing.unchecked(new UTFDataFormatException("encoded string too long: " + utflen + " bytes"));
		}

		putUnsignedShort(index, utflen);
		index += 2;
		for (int i = 0; i < strlen; i++) {
			int c = chars.charAt(i);
			if ((c >= 0x0001) && (c <= 0x007F)) {
				put(index++, (byte) c);
			} else if (c > 0x07FF) {
				put(index++, (byte) (0xE0 | ((c >> 12) & 0x0F)));
				put(index++, (byte) (0x80 | ((c >> 6) & 0x3F)));
				put(index++, (byte) (0x80 | ((c >> 0) & 0x3F)));
			} else {
				put(index++, (byte) (0xC0 | ((c >> 6) & 0x1F)));
				put(index++, (byte) (0x80 | ((c >> 0) & 0x3F)));
			}
		}
		return this;
	}

	public int readFrom(int index, int amount, ReadableByteChannel channel) throws IOException {
		int totalWritten = 0;
		ByteBuffer buf = ByteBuffer.allocate(1024);
		buf.limit(Math.min(buf.capacity(), Math.min(amount, remaining(index))));
		while (buf.hasRemaining() && channel.read(buf) >= 0) {
			buf.flip();
			int written = put(index, amount, buf);
			totalWritten += written;
			index += written;
			amount -= written;
			buf.position(0);
			buf.limit(Math.min(buf.capacity(), Math.min(amount, remaining(index))));
		}
		return totalWritten;
	}

	private int remaining(int index) {
		return length() - index;
	}

	public Bytes sliceBy(int offset) {
		return sliceBy(offset, length() - offset);
	}

	public Bytes sliceBy(int offset, int length) {
		return new Bytes() {

			@Override
			public byte get(int index) {
				return Bytes.this.get(offset + index);
			}

			@Override
			public int length() {
				return length;
			}

			@Override
			public Bytes put(int index, byte b) {
				Bytes.this.put(offset + index, b);
				return this;
			}
		};
	}

	public byte[] toByteArray() {
		byte[] result = new byte[length()];
		get(0, result);
		return result;
	}

	public int writeTo(int index, int amount, WritableByteChannel channel) throws IOException {
		int totalRead = 0;
		ByteBuffer buf = ByteBuffer.allocate(1024);
		while (remaining(index) > 0 && amount > 0) {
			int read = get(index, amount, buf);
			totalRead += read;
			index += read;
			amount -= read;
			buf.flip();
			while (buf.hasRemaining()) {
				channel.write(buf);
			}
			buf.clear();
		}
		return totalRead;
	}
}
