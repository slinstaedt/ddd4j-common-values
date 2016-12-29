package org.ddd4j.io.buffer;

public interface RelativeBuffer extends AutoCloseable {

	default int advancePosition(int count) {
		int oldPosition = position();
		int newPosition = oldPosition + count;
		if (newPosition < limit()) {
			position(newPosition);
		} else {
			throw new IllegalArgumentException();
		}
		return oldPosition;
	}

	Bytes bytes();

	default int capacity() {
		return bytes().length();
	}

	WriteBuffer clear();

	@Override
	default void close() {
		bytes().close();
	}

	default boolean hasRemaining() {
		return remaining() > 0;
	}

	int limit();

	RelativeBuffer limit(int newLimit);

	RelativeBuffer mark();

	default ByteOrder order() {
		return bytes().order();
	}

	default RelativeBuffer order(ByteOrder order) {
		bytes().order(order);
		return this;
	}

	int position();

	RelativeBuffer position(int newPosition);

	default int remaining() {
		return limit() - position();
	}

	RelativeBuffer reset();
}
