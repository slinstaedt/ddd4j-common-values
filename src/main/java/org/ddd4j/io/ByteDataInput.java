package org.ddd4j.io;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;

@FunctionalInterface
public interface ByteDataInput extends DataInput {

	default ObjectInput asObject() throws IOException {
		return new ObjectInputStream(asStream());
	}

	default InputStream asStream() {
		return new InputStream() {

			@Override
			public int read() throws IOException {
				try {
					return nextByte();
				} catch (EOFException e) {
					return -1;
				}
			}

			@Override
			public void close() throws IOException {
				throw new UnsupportedOperationException("Must close ByteDataInput directly");
			}
		};
	}

	int nextByte() throws IOException;

	default byte read() {
		int b = -1;
		try {
			b = nextByte();
		} catch (IOException e) {
			throw new IllegalStateException("Could not read data", e);
		}
		if (b == -1) {
			throw new IllegalStateException("End of stream reached");
		}
		return (byte) b;
	}

	@Override
	default boolean readBoolean() {
		return read() != 0;
	}

	@Override
	default byte readByte() {
		return read();
	}

	default byte[] readByteArray() {
		byte[] arr = new byte[readInt()];
		readFully(arr);
		return arr;
	}

	@Override
	default char readChar() {
		return (char) ((read() << 8) | (read() & 0xff));
	}

	@Override
	default double readDouble() {
		return Double.longBitsToDouble(readLong());
	}

	@Override
	default float readFloat() {
		return Float.intBitsToFloat(readInt());
	}

	@Override
	default void readFully(byte[] b) {
		readFully(b, 0, b.length);
	}

	@Override
	default void readFully(byte[] b, int off, int len) {
		if (off < 0 || len < 0 || off + len > b.length) {
			throw new IndexOutOfBoundsException();
		}
		for (int i = off; i < off + len; i++) {
			b[i] = read();
		}
	}

	@Override
	default int readInt() {
		return (((read() & 0xff) << 24) | ((read() & 0xff) << 16) | ((read() & 0xff) << 8) | (read() & 0xff));
	}

	@Override
	default String readLine() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	default long readLong() {
		return (((long) (read() & 0xff) << 56) | ((long) (read() & 0xff) << 48) | ((long) (read() & 0xff) << 40) | ((long) (read() & 0xff) << 32)
				| ((long) (read() & 0xff) << 24) | ((long) (read() & 0xff) << 16) | ((long) (read() & 0xff) << 8) | (read() & 0xff));
	}

	@Override
	default short readShort() {
		return (short) ((read() << 8) | (read() & 0xff));
	}

	@Override
	default int readUnsignedByte() {
		return Byte.toUnsignedInt(read());
	}

	@Override
	default int readUnsignedShort() {
		return (((read() & 0xff) << 8) | (read() & 0xff));
	}

	@Override
	default String readUTF() {
		int utflen = readUnsignedShort();
		byte[] bytearr = null;
		char[] chararr = null;
		bytearr = new byte[utflen];
		chararr = new char[utflen];

		int c, char2, char3;
		int count = 0;
		int chararr_count = 0;

		readFully(bytearr, 0, utflen);

		while (count < utflen) {
			c = bytearr[count] & 0xff;
			if (c > 127) {
				break;
			}
			count++;
			chararr[chararr_count++] = (char) c;
		}

		while (count < utflen) {
			c = bytearr[count] & 0xff;
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
				chararr[chararr_count++] = (char) c;
				break;
			case 12:
			case 13:
				/* 110x xxxx 10xx xxxx */
				count += 2;
				if (count > utflen) {
					throw new IllegalStateException("malformed input: partial character at end");
				}
				char2 = bytearr[count - 1];
				if ((char2 & 0xC0) != 0x80) {
					throw new IllegalStateException("malformed input around byte " + count);
				}
				chararr[chararr_count++] = (char) (((c & 0x1F) << 6) | (char2 & 0x3F));
				break;
			case 14:
				/* 1110 xxxx 10xx xxxx 10xx xxxx */
				count += 3;
				if (count > utflen) {
					throw new IllegalStateException("malformed input: partial character at end");
				}
				char2 = bytearr[count - 2];
				char3 = bytearr[count - 1];
				if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80)) {
					throw new IllegalStateException("malformed input around byte " + (count - 1));
				}
				chararr[chararr_count++] = (char) (((c & 0x0F) << 12) | ((char2 & 0x3F) << 6) | ((char3 & 0x3F) << 0));
				break;
			default:
				/* 10xx xxxx, 1111 xxxx */
				throw new IllegalStateException("malformed input around byte " + count);
			}
		}
		// The number of chars produced may be less than utflen
		return new String(chararr, 0, chararr_count);
	}

	@Override
	default int skipBytes(int n) {
		for (int i = 0; i < n; i++) {
			read();
		}
		return n;
	}
}
