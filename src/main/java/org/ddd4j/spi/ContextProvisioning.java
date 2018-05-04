package org.ddd4j.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import org.ddd4j.value.config.Configuration;

public interface ContextProvisioning {

	class JavaServiceLoader implements ContextProvisioning {

		@Override
		public <T> Iterable<T> loadRegistered(Class<T> type, ClassLoader loader) {
			return ServiceLoader.load(type, loader);
		}
	}

	class Programmatic implements ContextProvisioning {

		private final List<Object> registered;

		public Programmatic() {
			this.registered = new ArrayList<>();
		}

		@Override
		public <T> Iterable<T> loadRegistered(Class<T> type, ClassLoader loader) {
			return registered.stream().filter(type::isInstance).map(type::cast).collect(Collectors.toList());
		}

		public void with(Object any) {
			registered.add(any);
		}

		public void withConfigurer(ServiceConfigurer configurer) {
			registered.add(configurer);
		}
	}

	Ref<ContextProvisioning> REF = Ref.of(ContextProvisioning.class);

	static ContextProvisioning byJavaServiceLoader() {
		return new JavaServiceLoader();
	}

	static Programmatic programmatic() {
		return new Programmatic();
	}

	default Context createContext(Configuration configuration) {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Registry registry = Registry.create(configuration);
		registry.bind(REF).toInstance(this);
		loadRegistered(ServiceConfigurer.class, loader).forEach(registry::accept);
		registry.start();
		return registry;
	}

	default <T> Iterable<T> loadRegistered(Class<T> registeredType) {
		return loadRegistered(registeredType, registeredType.getClassLoader());
	}

	<T> Iterable<T> loadRegistered(Class<T> registeredType, ClassLoader loader);
}
