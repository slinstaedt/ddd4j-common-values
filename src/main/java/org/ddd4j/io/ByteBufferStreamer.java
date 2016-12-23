package org.ddd4j.io;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import org.ddd4j.infrastructure.Cache;

public class ByteBufferStreamer {

	public class ByteBufferInput implements Input {

		private final Deque<ByteBuffer> buffers;

		public ByteBufferInput(Collection<? extends ByteBuffer> buffers) {
			this.buffers = new ArrayDeque<>(buffers);
		}

		@Override
		public int available() throws IOException {
			return buffers.isEmpty() ? 0 : buffers.element().hasRemaining() ? buffers.element().remaining() : 1;
		}

		@Override
		public void close() {
			releaseAll(buffers);
		}

		@Override
		public int read() {
			ByteBuffer buffer = remaining(buffers);
			return buffer.hasRemaining() ? buffer.get() : -1;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int written = 0;
			ByteBuffer dst = ByteBuffer.wrap(b, off, len);
			while (dst.hasRemaining()) {
				ByteBuffer src = remaining(buffers);
				if (src.hasRemaining()) {
					written += copy(src, dst);
				} else {
					break;
				}
			}
			return written == 0 && len > 0 ? -1 : written;
		}
	}

	public class ByteBufferOutput implements Output {

		private final Deque<ByteBuffer> buffers;

		public ByteBufferOutput(Collection<ByteBuffer> buffers) {
			this.buffers = new ArrayDeque<>(buffers);
		}

		@Override
		public void close() {
			releaseAll(buffers);
		}

		public ByteBufferInput toInput() {
			buffers.forEach(Buffer::flip);
			return new ByteBufferInput(buffers);
		}

		@Override
		public void write(int b) {
			ensureCapacity(buffers).put((byte) b);
		}

		@Override
		public void write(byte[] b, int off, int len) {
			ByteBuffer src = ByteBuffer.wrap(b, off, len);
			while (src.hasRemaining()) {
				copy(src, ensureCapacity(buffers));
			}
		}
	}

	private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

	public static int copy(ByteBuffer src, ByteBuffer dst) {
		int bytesToCopy = Math.min(src.remaining(), dst.remaining());
		if (bytesToCopy > 0) {
			ByteBuffer tmp = src.duplicate();
			tmp.limit(tmp.position() + bytesToCopy);
			dst.put(tmp);
			src.position(src.position() + bytesToCopy);
		}
		return bytesToCopy;
	}

	private final int bufferSize;
	private final Cache<Integer, ByteBuffer> cache;

	public ByteBufferStreamer(int bufferSize) {
		this.bufferSize = bufferSize;
		this.cache = Cache.createPooled(ByteBuffer::capacity);
	}

	private ByteBuffer ensureCapacity(Deque<ByteBuffer> buffers) {
		ByteBuffer buffer = buffers.isEmpty() ? null : buffers.getLast();
		if (buffer == null || !buffer.hasRemaining()) {
			buffer = cache.acquire(bufferSize);
			buffers.addLast(buffer);
		}
		return buffer;
	}

	public ByteBufferInput newInput(List<ByteBuffer> buffers) {
		return new ByteBufferInput(buffers);
	}

	public ByteBufferOutput newOutput(ByteBuffer... buffers) {
		return new ByteBufferOutput(Arrays.asList(buffers));
	}

	private void releaseAll(Collection<ByteBuffer> buffers) {
		buffers.forEach(cache::release);
		buffers.clear();
	}

	private ByteBuffer remaining(Deque<ByteBuffer> buffers) {
		Iterator<ByteBuffer> iterator = buffers.iterator();
		while (iterator.hasNext()) {
			ByteBuffer buffer = iterator.next();
			if (buffer.hasRemaining()) {
				return buffer;
			} else {
				iterator.remove();
				buffer.clear();
				cache.release(buffer);
			}
		}
		return EMPTY;
	}
}
