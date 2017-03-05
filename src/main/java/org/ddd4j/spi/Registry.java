package org.ddd4j.spi;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.ddd4j.collection.Cache;
import org.ddd4j.contract.Require;
import org.ddd4j.value.collection.Configuration;

public class Registry implements Context, ServiceBinder {

	private class TargetContext extends Registry {

		TargetContext(Key<?> target) {
		}
	}

	private Configuration configuration;
	private final Map<Key<?>, ServiceFactory<?>> factories;
	private final Map<Key<?>, TargetContext> targets;
	private final Cache<Key<?>, Object> services;

	private Registry() {
		this.factories = new HashMap<>();
		this.targets = new HashMap<>();
		this.services = Cache.sharedOnEqualKey();
	}

	@Override
	public <T> void bind(Key<T> key, ServiceFactory<? extends T> factory, Key<?>... targetKeys) {
		Require.nonNullElements(key, factory);
		if (targetKeys.length == 0) {
			factories.put(key, factory);
		} else {
			Arrays.stream(targetKeys).forEach(t -> targets.computeIfAbsent(t, TargetContext::new).bind(key, factory));
		}
	}

	@SuppressWarnings("unchecked")
	private <T> ServiceFactory<T> factoryFor(Key<T> key) {
		ServiceFactory<T> factory = (ServiceFactory<T>) factories.get(key);
		if (factory == null) {
			factory = key;
		}
		return factory;
	}

	@Override
	public <T> T get(Key<T> key) {
		Object instance = services.get(key);
		if (instance == null) {
			instance = factoryFor(key).create(this, configuration.prefixed(key.name()));
		}
		return (T) instance;
	}
}