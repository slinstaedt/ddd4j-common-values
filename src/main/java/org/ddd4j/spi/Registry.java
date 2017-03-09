package org.ddd4j.spi;

import java.util.HashMap;
import java.util.Map;

import org.ddd4j.collection.Cache;
import org.ddd4j.contract.Require;
import org.ddd4j.value.collection.Configuration;

public class Registry implements Context, ServiceBinder {

	private static class Dependent extends Registry {

		private final Registry parent;

		Dependent(Configuration configuration, Registry parent) {
			super(configuration);
			this.parent = Require.nonNull(parent);
		}

		@Override
		public <T> T get(Key<T> key) {
			if (hasRegisteredFactory(key)) {
				return super.get(key);
			} else {
				return parent.get(key);
			}
		}
	}

	private final Configuration configuration;
	@SuppressWarnings("rawtypes")
	private final Cache.Aside<Key, Object> services;
	private final Map<Key<?>, Registry> dependents;
	private final Map<Key<?>, ServiceFactory<?>> factories;

	public Registry(Configuration configuration) {
		this.configuration = Require.nonNull(configuration);
		this.services = Cache.sharedOnEqualKey();
		this.dependents = new HashMap<>();
		this.factories = new HashMap<>();
	}

	@Override
	public <T> void bind(Key<T> key, ServiceFactory<? extends T> factory, Key<?>... path) {
		dependentOfPath(path).factories.put(key, factory);
	}

	@Override
	public void close() {
		dependents.values().forEach(Context::close);
		dependents.clear();
		services.evictAll(this::destroyService);
	}

	private <T> T createService(Key<T> key) throws Exception {
		return factoryFor(key).create(this, configuration.prefixed(key.name()));
	}

	private Registry dependent(Key<?> target) {
		return dependents.computeIfAbsent(target, t -> new Dependent(configuration.prefixed(target.name()), this));
	}

	public Context dependentContext(Key<?> key) {
		return dependents.getOrDefault(key, this);
	}

	private Registry dependentOfPath(Key<?>... path) {
		@SuppressWarnings("resource")
		Registry current = this;
		for (Key<?> target : path) {
			current = current.dependent(target);
		}
		return current;
	}

	private <T> void destroyService(Key<T> key, T service) throws Exception {
		factoryFor(key).destroy(service);
	}

	private <T> ServiceFactory<T> factoryFor(Key<T> key) {
		@SuppressWarnings("unchecked")
		ServiceFactory<T> factory = (ServiceFactory<T>) factories.get(key);
		if (factory == null) {
			factory = key;
		}
		return factory;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T get(Key<T> key) {
		return (T) services.acquire(key, this::createService);
	}

	boolean hasRegisteredFactory(Key<?> key) {
		return factories.containsKey(key);
	}

	public Context newDependentContext() {
		return new Dependent(configuration, this);
	}
}