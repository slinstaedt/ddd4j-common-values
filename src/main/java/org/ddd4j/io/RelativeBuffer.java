package org.ddd4j.io;

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

	Bytes backing();

	default int capacity() {
		return backing().length();
	}

	WriteBuffer clear();

	@Override
	default void close() {
		backing().close();
	}

	default boolean hasRemaining() {
		return remaining() > 0;
	}

	int limit();

	RelativeBuffer limit(int newLimit);

	RelativeBuffer limitToRemaining(int remaining);

	RelativeBuffer mark();

	default ByteOrder order() {
		return backing().order();
	}

	default RelativeBuffer order(ByteOrder order) {
		backing().order(order);
		return this;
	}

	int position();

	RelativeBuffer position(int newPosition);

	default int remaining() {
		return limit() - position();
	}

	RelativeBuffer reset();
}
