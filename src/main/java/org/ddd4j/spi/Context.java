package org.ddd4j.spi;

import org.ddd4j.value.Named;
import org.ddd4j.value.collection.Configuration;

public interface Context {

	Context child(Named value);

	// TODO rename to getConfig?
	Configuration configuration();

	default <V> V conf(Configuration.Key<V> key) {
		return configuration().get(key);
	}

	<T> T get(Key<T> key);

	<T extends Named> T specific(Key<T> key, String name);
}
