package org.ddd4j.spi;

public interface Context {

	<T> T get(Key<T> key);
}
