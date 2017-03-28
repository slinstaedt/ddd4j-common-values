package org.ddd4j.spi;

import java.util.ServiceLoader;

import org.ddd4j.value.collection.Configuration;

public interface ContextProvisioning {

	class JavaServiceLoader implements ContextProvisioning {

		@Override
		public <T> Iterable<T> loadRegistered(Class<T> type, ClassLoader loader) {
			return ServiceLoader.load(type, loader);
		}
	}

	Key<ContextProvisioning> KEY = Key.of(ContextProvisioning.class);

	static ContextProvisioning byJavaServiceLoader() {
		return new JavaServiceLoader();
	}

	default Context buildContext(Configuration configuration) {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Registry registry = Registry.create(configuration);
		registry.bind(KEY).toInstance(this);
		loadRegistered(ServiceProvider.class, loader).forEach(registry::accept);
		registry.start();
		return registry;
	}

	default <T> Iterable<T> loadRegistered(Class<T> registeredType) {
		return loadRegistered(registeredType, registeredType.getClassLoader());
	}

	<T> Iterable<T> loadRegistered(Class<T> registeredType, ClassLoader loader);
}
