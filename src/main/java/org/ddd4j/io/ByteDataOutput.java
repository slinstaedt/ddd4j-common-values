package org.ddd4j.io;

import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

@FunctionalInterface
public interface ByteDataOutput extends DataOutput {

	class AppendingObjectOutputStream extends ObjectOutputStream {

		public AppendingObjectOutputStream(OutputStream out) throws IOException {
			super(out);
		}

		@Override
		protected void writeStreamHeader() throws IOException {
			reset();
		}
	}

	default ObjectOutput asObject(boolean append) throws IOException {
		return append ? new AppendingObjectOutputStream(asStream()) : new ObjectOutputStream(asStream());
	}

	default OutputStream asStream() {
		return new OutputStream() {

			@Override
			public void write(int b) throws IOException {
				nextByte((byte) (0xff & b));
			}

			@Override
			public void close() throws IOException {
				throw new UnsupportedOperationException("Must close ByteDataOutput directly");
			}
		};
	}

	void nextByte(byte b) throws IOException;

	default void write(byte b) {
		try {
			nextByte(b);
		} catch (IOException e) {
			throw new IllegalStateException("Could not write data", e);
		}
	}

	@Override
	default void write(byte b[]) {
		write(b, 0, b.length);
	}

	@Override
	default void write(byte b[], int off, int len) {
		if (off < 0 || len < 0 || off + len > b.length) {
			throw new IndexOutOfBoundsException();
		}
		for (int i = off; i < off + len; i++) {
			write(b[i]);
		}
	}

	@Override
	default void write(int b) {
		write((byte) (0xff & b));
	}

	@Override
	default void writeBoolean(boolean v) {
		write(v ? (byte) 1 : 0);
	}

	@Override
	default void writeByte(int v) {
		write(v);
	}

	default void writeByteArray(byte[] arr) {
		writeInt(arr.length);
		write(arr);
	}

	@Override
	default void writeBytes(String s) {
		for (int i = 0; i < s.length(); i++) {
			write((byte) (0xff & s.charAt(i)));
		}
	}

	default void writeChar(char v) {
		write((byte) (0xff & (v >> 8)));
		write((byte) (0xff & v));
	}

	@Override
	default void writeChar(int v) {
		write((byte) (0xff & (v >> 8)));
		write((byte) (0xff & v));
	}

	@Override
	default void writeChars(String s) {
		for (int i = 0; i < s.length(); i++) {
			writeChar(s.charAt(i));
		}
	}

	@Override
	default void writeDouble(double v) {
		writeLong(Double.doubleToLongBits(v));
	}

	@Override
	default void writeFloat(float v) {
		writeInt(Float.floatToIntBits(v));
	}

	@Override
	default void writeInt(int v) {
		write((byte) (0xff & (v >> 24)));
		write((byte) (0xff & (v >> 16)));
		write((byte) (0xff & (v >> 8)));
		write((byte) (0xff & v));
	}

	@Override
	default void writeLong(long v) {
		write((byte) (0xff & (v >> 56)));
		write((byte) (0xff & (v >> 48)));
		write((byte) (0xff & (v >> 40)));
		write((byte) (0xff & (v >> 32)));
		write((byte) (0xff & (v >> 24)));
		write((byte) (0xff & (v >> 16)));
		write((byte) (0xff & (v >> 8)));
		write((byte) (0xff & v));
	}

	@Override
	default void writeShort(int v) throws IOException {
		write((byte) (0xff & (v >> 8)));
		write((byte) (0xff & v));
	}

	@Override
	default void writeUTF(String s) {
		int strlen = s.length();
		int utflen = 0;
		int c, count = 0;

		/* use charAt instead of copying String to char array */
		for (int i = 0; i < strlen; i++) {
			c = s.charAt(i);
			if ((c >= 0x0001) && (c <= 0x007F)) {
				utflen++;
			} else if (c > 0x07FF) {
				utflen += 3;
			} else {
				utflen += 2;
			}
		}

		if (utflen > 65535) {
			throw new IllegalArgumentException("encoded string too long: " + utflen + " bytes");
		}

		byte[] bytearr = null;
		bytearr = new byte[utflen + 2];

		bytearr[count++] = (byte) ((utflen >>> 8) & 0xFF);
		bytearr[count++] = (byte) ((utflen >>> 0) & 0xFF);

		int i = 0;
		for (i = 0; i < strlen; i++) {
			c = s.charAt(i);
			if (!((c >= 0x0001) && (c <= 0x007F))) {
				break;
			}
			bytearr[count++] = (byte) c;
		}

		for (; i < strlen; i++) {
			c = s.charAt(i);
			if ((c >= 0x0001) && (c <= 0x007F)) {
				bytearr[count++] = (byte) c;

			} else if (c > 0x07FF) {
				bytearr[count++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
				bytearr[count++] = (byte) (0x80 | ((c >> 6) & 0x3F));
				bytearr[count++] = (byte) (0x80 | ((c >> 0) & 0x3F));
			} else {
				bytearr[count++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
				bytearr[count++] = (byte) (0x80 | ((c >> 0) & 0x3F));
			}
		}
		write(bytearr, 0, utflen + 2);
	}
}
