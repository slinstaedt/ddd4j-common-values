package org.ddd4j.spi;

import java.util.Optional;
import java.util.function.Supplier;

import org.ddd4j.util.Require;
import org.ddd4j.util.value.Named;
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

	// TODO rename to getConfig?
	Configuration configuration();

	default <V> Supplier<V> confProvider(ConfKey<V> key) {
		Require.nonNull(key);
		return () -> conf(key);
	}

	<T> T get(Ref<T> ref);

	default <T extends Named> NamedService<T> specific(Ref<T> ref) {
		Require.nonNull(ref);
		return name -> specific(ref, name);
	}

	default <T extends Named> T specific(Ref<T> ref, ConfKey<String> key) {
		String name = conf(key);
		return specific(ref).withOrFail(name);
	}

	<T extends Named> Optional<T> specific(Ref<T> ref, String name);
}
