package org.ddd4j.spi;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;

import org.ddd4j.util.Require;
import org.ddd4j.util.value.Named;
import org.ddd4j.util.value.Sequence;
import org.ddd4j.value.config.ConfKey;
import org.ddd4j.value.config.Configuration;

public interface Context {

	class IndexedService<T extends Named> {

		private final Sequence<T> services;

		public IndexedService(Sequence<T> services) {
			this.services = Require.nonEmpty(services);
		}

		public T get(int index) {
			return services.get(index);
		}

		public int index() {
			return services.size() - 1;
		}

		public T service() {
			return get(index());
		}
	}

	interface NamedService<T extends Named> {

		Optional<T> with(String name);

		default T withOrFail(String name) {
			return with(name).orElseThrow(() -> new IllegalArgumentException("Service with name not registerd: " + name));
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

	default <T extends Named> IndexedService<T> specific(Ref<T> ref, ConfKey<Sequence<String>> key) {
		Sequence<T> services = conf(key).ifEmpty(() -> {
			throw new IllegalArgumentException("No service names given configured for ref: " + ref);
		}).map(name -> specific(ref, name)
				.orElseThrow(() -> new NoSuchElementException("No service with name '" + name + "' registered for ref: " + ref)));
		return new IndexedService<>(services.copy());
	}

	<T extends Named> Optional<T> specific(Ref<T> ref, String name);
}
