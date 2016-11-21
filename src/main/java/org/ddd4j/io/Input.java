package org.ddd4j.io;

import java.io.Closeable;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.ddd4j.contract.Require;

@FunctionalInterface
public interface Input extends Closeable {

	class Stream extends InputStream {

		private final Input input;

		public Stream(Input input) {
			this.input = Require.nonNull(input);
		}

		@Override
		public int available() throws IOException {
			return input.available();
		}

		@Override
		public void close() throws IOException {
			input.close();
		}

		@Override
		public int read() throws IOException {
			return input.read();
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			return input.read(b, off, len);
		}
	}

	static Input wrap(InputStream stream) {
		Require.nonNull(stream);
		return new Input() {

			@Override
			public int available() throws IOException {
				return stream.available();
			}

			@Override
			public void close() throws IOException {
				stream.close();
			}

			@Override
			public int read() throws IOException {
				return stream.read();
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				return stream.read(b, off, len);
			}
		};
	}

	static Input wrap(ReadableByteChannel channel) {
		Require.nonNull(channel);
		return new Input() {

			private final ByteBuffer buffer = ByteBuffer.allocate(1024);

			private ByteBuffer buffer() throws IOException {
				if (!buffer.hasRemaining()) {
					buffer.clear();
					channel.read(buffer);
					buffer.flip();
				}
				return buffer;
			}

			@Override
			public int available() throws IOException {
				return buffer().remaining();
			}

			@Override
			public void close() throws IOException {
				if (channel.isOpen()) {
					channel.close();
				}
			}

			@Override
			public int read() throws IOException {
				return buffer().hasRemaining() ? buffer().get() : -1;
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				int written = Math.min(buffer().remaining(), len);
				buffer().get(b, off, written);
				return written == 0 && len > 0 ? -1 : written;
			}
		};
	}

	default DataInput asDataInput() {
		return new DataInputStream(asStream());
	}

	default ObjectInput asObjectInput() throws IOException {
		return new ObjectInputStream(asStream());
	}

	default ReadableByteChannel asByteChannel() {
		return Channels.newChannel(asStream());
	}

	default InputStream asStream() {
		return new Stream(this);
	}

	default int available() throws IOException {
		return 1;
	}

	@Override
	default void close() throws IOException {
	}

	int read() throws IOException;

	default int read(byte b[], int off, int len) throws IOException {
		if (off < 0 || len < 0 || len > b.length - off) {
			throw new IndexOutOfBoundsException();
		} else if (len == 0) {
			return 0;
		}

		int c = read();
		if (c == -1) {
			return -1;
		}
		b[off] = (byte) c;
		int i = 1;
		try {
			for (; i < len; i++) {
				c = read();
				if (c == -1) {
					break;
				}
				b[off + i] = (byte) c;
			}
		} catch (IOException ee) {
		}
		return i;
	}

	default byte[] readByteArray() throws IOException {
		DataInput input = asDataInput();
		byte[] arr = new byte[input.readInt()];
		input.readFully(arr);
		return arr;
	}
}