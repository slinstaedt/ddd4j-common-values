package org.ddd4j.io;

@FunctionalInterface
public interface Reader<T> {

	T read();
}
