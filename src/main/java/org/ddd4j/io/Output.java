package org.ddd4j.io;

import java.io.Closeable;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

import org.ddd4j.contract.Require;

@FunctionalInterface
public interface Output extends Closeable, Flushable {

	class AppendingObjectOutputStream extends ObjectOutputStream {

		public AppendingObjectOutputStream(OutputStream out) throws IOException {
			super(out);
		}

		@Override
		protected void writeStreamHeader() throws IOException {
			reset();
		}
	}

	class Stream extends OutputStream {

		private final Output output;

		private Stream(Output output) {
			this.output = Require.nonNull(output);
		}

		@Override
		public void close() throws IOException {
			output.close();
		}

		@Override
		public void flush() throws IOException {
			output.flush();
		}

		@Override
		public void write(int b) throws IOException {
			output.write(b);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			output.write(b, off, len);
		}
	}

	static Output wrap(OutputStream stream) {
		Require.nonNull(stream);
		return new Output() {

			@Override
			public void flush() throws IOException {
				stream.flush();
			}

			@Override
			public void close() throws IOException {
				stream.close();
			}

			@Override
			public void write(int b) throws IOException {
				stream.write(b);
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				stream.write(b, off, len);
			}
		};
	}

	static Output wrap(WritableByteChannel channel) {
		Require.nonNull(channel);
		return new Output() {

			private final ByteBuffer oneByte = ByteBuffer.allocate(1);

			@Override
			public void close() throws IOException {
				if (channel.isOpen()) {
					channel.close();
				}
			}

			@Override
			public void write(int b) throws IOException {
				oneByte.clear();
				oneByte.put((byte) b).flip();
				channel.write(oneByte);
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				channel.write(ByteBuffer.wrap(b, off, len));
			}
		};
	}

	default WritableByteChannel asByteChannel() {
		return Channels.newChannel(asStream());
	}

	default DataOutput asDataOutput() {
		return new DataOutputStream(asStream());
	}

	default ObjectOutput asObjectOutput(boolean append) throws IOException {
		return append ? new AppendingObjectOutputStream(asStream()) : new ObjectOutputStream(asStream());
	}

	default OutputStream asStream() {
		return new Stream(this);
	}

	@Override
	default void close() throws IOException {
	}

	@Override
	default void flush() throws IOException {
	}

	void write(int b) throws IOException;

	default void write(byte b[], int off, int len) throws IOException {
		if (off < 0 || len < 0 || off + len > b.length) {
			throw new IndexOutOfBoundsException();
		}
		for (int i = 0; i < len; i++) {
			write(b[off + i]);
		}
	}

	default void writeByteArray(byte[] arr) throws IOException {
		DataOutput output = asDataOutput();
		output.writeInt(arr.length);
		output.write(arr);
	}
}
