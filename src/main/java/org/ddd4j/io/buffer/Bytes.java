package org.ddd4j.io.buffer;

import java.io.UTFDataFormatException;
import java.nio.ByteBuffer;

import org.ddd4j.contract.Require;
import org.ddd4j.io.buffer.ByteOrder.IndexedBytes;
import org.ddd4j.value.Throwing;

public abstract class Bytes implements IndexedBytes, AutoCloseable {

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
		amount = Math.min(amount, length() - index);
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
		amount = Math.min(amount, length() - index);
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
}
