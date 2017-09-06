package org.ddd4j.spi;

import java.util.Optional;
import java.util.function.Supplier;

import org.ddd4j.Require;
import org.ddd4j.value.Named;
import org.ddd4j.value.config.ConfKey;
import org.ddd4j.value.config.Configuration;

public interface Context {

	interface NamedService<T extends Named> {

		Optional<T> with(String name);

		default T withOrFail(String name) {
			return with(name).orElseThrow(() -> new IllegalArgumentException("Service with name not registerd: " + name));
		}
	}

	Context child(Named value);

	default <V> V conf(ConfKey<V> key) {
		return configuration().get(key);
	}

	default <V> Supplier<V> confProvider(ConfKey<V> key) {
		return () -> conf(key);
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
