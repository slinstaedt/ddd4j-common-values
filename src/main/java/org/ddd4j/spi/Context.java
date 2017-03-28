package org.ddd4j.spi;

import org.ddd4j.value.Named;

public interface Context extends AutoCloseable {

	Context child(Named value);

	@Override
	void close();

	<T> T get(Key<T> key);
}
