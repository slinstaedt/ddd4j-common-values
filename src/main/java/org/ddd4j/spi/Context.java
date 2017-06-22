package org.ddd4j.spi;

import java.util.Optional;

import org.ddd4j.Require;
import org.ddd4j.value.Named;
import org.ddd4j.value.collection.Configuration;

public interface Context {

	interface NamedService<T extends Named> {

		Optional<T> with(String name);
	}

	Context child(Named value);

	default <V> V conf(Configuration.Key<V> key) {
		return configuration().get(key);
	}

	// TODO rename to getConfig?
	Configuration configuration();

	<T> T get(Key<T> key);

	default <T extends Named> NamedService<T> specific(Key<T> key) {
		Require.nonNull(key);
		return name -> specific(key, name);
	}

	<T extends Named> Optional<T> specific(Key<T> key, String name);
}
