package org.ddd4j.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.ddd4j.contract.Require;

public class Buffer implements ReadBuffer, WriteBuffer {

	private final Bytes bytes;

	private int position;
	private int limit;
	private int mark;

	public Buffer(Bytes bytes) {
		this(bytes, 0, bytes.length(), -1);
	}

	private Buffer(Bytes bytes, int position, int limit, int mark) {
		this.bytes = Require.nonNull(bytes);
		this.position = position;
		this.limit = limit;
		this.mark = mark;
	}

	@Override
	public InputStream asInputStream() {
		return new InputStream() {

			@Override
			public int available() throws IOException {
				return remaining();
			}

			@Override
			public void close() throws IOException {
				Buffer.this.close();
			}

			@Override
			public synchronized void mark(int readlimit) {
				Buffer.this.mark();
			}

			@Override
			public boolean markSupported() {
				return true;
			}

			@Override
			public int read() throws IOException {
				return hasRemaining() ? get() + 128 : -1;
			}

			@Override
			public synchronized void reset() throws IOException {
				Buffer.this.reset();
			}
		};
	}

	@Override
	public OutputStream asOutputStream() {
		return new OutputStream() {

			@Override
			public void close() throws IOException {
				Buffer.this.close();
			}

			@Override
			public void write(int b) throws IOException {
				put((byte) b);
			}
		};
	}

	@Override
	public Bytes backing() {
		return bytes;
	}

	@Override
	public Buffer clear() {
		return limitTo(capacity());
	}

	@Override
	public Buffer duplicate() {
		return new Buffer(bytes, position, limit, mark);
	}

	@Override
	public Buffer flip() {
		return limitTo(position);
	}

	@Override
	public int limit() {
		return limit;
	}

	@Override
	public Buffer limit(int newLimit) {
		Require.that(newLimit >= 0);
		limit = newLimit;
		if (position > limit) {
			position = limit;
		}
		if (mark > limit) {
			mark = -1;
		}
		return this;
	}

	private Buffer limitTo(int newLimit) {
		limit = newLimit;
		position = 0;
		mark = -1;
		return this;
	}

	@Override
	public Buffer mark() {
		mark = position;
		return this;
	}

	@Override
	public Buffer order(ByteOrder order) {
		bytes.order(order);
		return this;
	}

	@Override
	public int position() {
		return position;
	}

	@Override
	public Buffer position(int newPosition) {
		Require.that(newPosition >= 0);
		position = newPosition;
		if (mark > position) {
			mark = -1;
		}
		return this;
	}

	@Override
	public Buffer reset() {
		Require.that(mark >= 0);
		position = mark;
		return this;
	}

	@Override
	public Buffer rewind() {
		position = 0;
		mark = -1;
		return this;
	}
}
