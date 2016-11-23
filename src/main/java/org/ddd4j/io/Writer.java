package org.ddd4j.io;

@FunctionalInterface
public interface Writer<T> {

	void write(T value);
}
