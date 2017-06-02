package org.ddd4j.spi;

import org.ddd4j.value.Named;
import org.ddd4j.value.collection.Configuration;

public interface Context extends AutoCloseable {

	Context child(Named value);

	// TODO rename to getConfig?
	Configuration configuration();

	default <V> V conf(Configuration.Key<V> key) {
		return configuration().get(key);
	}

	@Override
	void close();

	<T> T get(Key<T> key);
}
