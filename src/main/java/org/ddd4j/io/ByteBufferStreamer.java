package org.ddd4j.io;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;

public class ByteBufferStreamer {

	public class ByteBufferInputStream extends InputStream {

		private final Deque<ByteBuffer> buffers;

		public ByteBufferInputStream(Collection<? extends ByteBuffer> buffers) {
			this.buffers = new ArrayDeque<>(buffers);
		}

		@Override
		public void close() {
			releaseAll(buffers);
		}

		@Override
		public int read() {
			while (!buffers.isEmpty()) {
				if (buffers.element().hasRemaining()) {
					return buffers.element().get();
				} else {
					checkRemaining(buffers);
				}
			}
			return -1;
		}

		@Override
		public int read(byte[] dst) {
			return read(dst, 0, dst.length);
		}

		@Override
		public int read(byte[] dst, int offset, int length) {
			int read = buffers.isEmpty() ? -1 : 0;
			while (!buffers.isEmpty()) {
				int remaining = Math.min(length, buffers.element().remaining());
				if (remaining > 0) {
					buffers.element().get(dst, offset, remaining);
					offset += remaining;
					length -= remaining;
					read += remaining;
				} else {
					checkRemaining(buffers);
				}
			}
			return read;
		}
	}

	public class ByteBufferOutputStream extends OutputStream {

		private final Deque<ByteBuffer> buffers;

		private ByteBufferOutputStream(Collection<? extends ByteBuffer> buffers) {
			this.buffers = new ArrayDeque<>(buffers);
		}

		@Override
		public void close() {
			releaseAll(buffers);
		}

		public ByteBufferInputStream toInputStream() {
			buffers.forEach(Buffer::flip);
			return new ByteBufferInputStream(buffers);
		}

		@Override
		public void write(byte[] src) {
			write(src, 0, src.length);
		}

		@Override
		public void write(byte[] src, int offset, int length) {
			while (length > 0) {
				ByteBuffer buffer = ensureCapacity(buffers);
				int remaining = buffer.remaining();
				buffer.put(src, offset, remaining);
				offset += remaining;
				length -= remaining;
			}
		}

		@Override
		public void write(int b) {
			ensureCapacity(buffers).put((byte) b);
		}
	}

	private final int bufferSize;
	private final Cache<Integer, ByteBuffer> cache;

	public ByteBufferStreamer(int bufferSize) {
		this.bufferSize = bufferSize;
		this.cache = Cache.createPooled(ByteBuffer::capacity);
	}

	private boolean checkRemaining(Deque<ByteBuffer> buffers) {
		while (!buffers.isEmpty()) {
			ByteBuffer buffer = buffers.element();
			if (buffer.hasRemaining()) {
				return true;
			} else {
				// TODO
			}
		}
		return false;
	}

	private ByteBuffer ensureCapacity(Deque<ByteBuffer> buffers) {
		ByteBuffer buffer = buffers.isEmpty() ? null : buffers.getLast();
		if (buffer == null || !buffer.hasRemaining()) {
			buffer = cache.acquire(bufferSize);
			buffers.add(buffer);
		}
		return buffer;
	}

	public ByteBufferInputStream newInputStream() {
		return new ByteBufferInputStream(Collections.emptyList());
	}

	public ByteBufferOutputStream newOutputStream() {
		return new ByteBufferOutputStream(Collections.emptyList());
	}

	private void releaseAll(Collection<ByteBuffer> buffers) {
		buffers.forEach(cache::release);
		buffers.clear();
	}
}
