package org.ddd4j.spi;

public interface Context extends AutoCloseable {

	@Override
	void close();

	<T> T get(Key<T> key);
}
