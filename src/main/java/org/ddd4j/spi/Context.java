package org.ddd4j.spi;

import org.ddd4j.value.Throwing.Closeable;

public interface Context extends Closeable {

	<T> T get(Key<T> key);
}
