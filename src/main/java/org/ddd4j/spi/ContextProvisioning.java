package org.ddd4j.spi;

import java.util.ServiceLoader;

import org.ddd4j.value.collection.Configuration;

public interface ContextProvisioning {

	class JavaServiceLoaderProvisioning implements ContextProvisioning {

		@Override
		public <T> Iterable<T> loadRegistered(Class<T> type) {
			return ServiceLoader.load(type, type.getClassLoader());
		}
	}

	Key<ContextProvisioning> KEY = Key.of(ContextProvisioning.class);

	static ContextProvisioning get() {
		return new JavaServiceLoaderProvisioning();
	}

	default Context buildContext(Configuration configuration) {
		Registry registry = new Registry(configuration);
		loadRegistered(ServiceProvider.class).forEach(registry::accept);
		registry.bind(KEY).toInstance(this);
		return registry;
	}

	<T> Iterable<T> loadRegistered(Class<T> registeredType);
}
