package org.ddd4j.spi;

import java.util.HashMap;
import java.util.Map;

import org.ddd4j.collection.Cache;
import org.ddd4j.value.collection.Configuration;

@SuppressWarnings("unchecked")
public class Registry implements Context, ServiceBinder {

	private static class TargetContext extends Registry {

		private final Registry parent;
		private final Key<?> target;

		TargetContext(Registry parent, Key<?> target) {
			this.parent = parent;
			this.target = target;
		}

		@Override
		protected <T> ServiceFactory<T> factoryFor(Key<T> key) {
			ServiceFactory<T> factory = super.factoryFor(key);
			if (factory == key) {
				factory = parent.factoryFor(key);
			}
			return factory;
		}
	}

	private Configuration configuration;
	private final Map<Key<?>, ServiceFactory<?>> factories;
	private final Map<Key<?>, TargetContext> targets;
	@SuppressWarnings("rawtypes")
	private final Cache.Aside<Key, Object> services;

	private Registry() {
		this.factories = new HashMap<>();
		this.targets = new HashMap<>();
		this.services = Cache.sharedOnEqualKey();
	}

	private <T> void bind(Key<T> key, ServiceFactory<? extends T> factory) {
		factories.put(key, factory);
	}

	@Override
	public <T> void bind(Key<T> key, ServiceFactory<? extends T> factory, Key<?>... path) {
		childOfPath(path).bind(key, factory);
	}

	private Registry childOf(Key<?> target) {
		return targets.computeIfAbsent(target, t -> new TargetContext(this, t));
	}

	private Registry childOfPath(Key<?>... path) {
		Registry current = this;
		for (Key<?> target : path) {
			current = current.childOf(target);
		}
		return current;
	}

	public Context newDependentContext() {
		return new TargetContext(this, null);
	}

	protected <T> ServiceFactory<T> factoryFor(Key<T> key) {
		ServiceFactory<T> factory = (ServiceFactory<T>) factories.get(key);
		if (factory == null) {
			factory = key;
		}
		return factory;
	}

	private <T> T createService(Key<T> key) throws Exception {
		return factoryFor(key).create(this, configuration.prefixed(key.name()));
	}

	private <T> void destroyService(Key<T> key, T service) throws Exception {
		factoryFor(key).destroy(service);
	}

	@Override
	public <T> T get(Key<T> key) {
		return (T) services.acquire(key, this::createService);
	}

	@Override
	public void closeChecked() throws Exception {
		services.evictAll(this::destroyService);
		targets.values().forEach(Context::close);
		targets.clear();
	}
}