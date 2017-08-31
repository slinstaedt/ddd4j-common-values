package org.ddd4j.io;

@FunctionalInterface
public interface ByteConsumer<E extends Throwable> {

	void accept(byte b) throws E;
}
