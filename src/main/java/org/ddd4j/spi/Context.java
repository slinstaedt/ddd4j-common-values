package org.ddd4j.spi;

import java.util.Optional;
import java.util.function.Supplier;

import org.ddd4j.util.Require;
import org.ddd4j.util.value.Named;
import org.ddd4j.util.value.Sequence;
import org.ddd4j.value.config.ConfKey;
import org.ddd4j.value.config.Configuration;

public interface Context {

	interface IndexedService<T extends Named> {

		Sequence<T> all();

		default T get(int index) {
			return all().get(index);
		}

		default int index() {
			return all().size() - 1;
		}

		default T service() {
			return get(index());
		}
	}

	interface NamedService<T extends Named> {

		default T with(String name) {
			return withOptional(name).orElseThrow(() -> new IllegalArgumentException("Service with name not registerd: " + name));
		}

		Optional<T> withOptional(String name);
	}

	interface Services<T extends Named> {

		int index();

		T indexed(int index);

		T named(String name);

		default T service() {
			return indexed(index());
		}
	}

	Context child(Named value);

	default <V> V conf(ConfKey<V> key) {
		return key.valueOf(configuration());
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

	default <T extends Named> IndexedService<T> specific(Ref<T> ref, ConfKey<String[]> key) {
		String[] serviceNames = conf(key);
		if (serviceNames.length > 0) {
			throw new IllegalArgumentException("No service names given configured for ref: " + ref);
		}
		Sequence<T> services = Sequence.of(serviceNames).map(name -> specific(ref).with(name.trim())).copy();
		return () -> services;
	}

	<T extends Named> Optional<T> specific(Ref<T> ref, String name);
}
