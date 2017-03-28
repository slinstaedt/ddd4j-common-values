package org.ddd4j.spi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.ddd4j.Require;
import org.ddd4j.collection.Cache;
import org.ddd4j.value.Named;
import org.ddd4j.value.collection.Configuration;

public abstract class Registry implements Context, ServiceBinder {

	private static class Child extends Registry {

		private final Registry parent;

		Child(Configuration configuration, Registry parent) {
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

		@Override
		public void initializeEager(Key<?> key) {
			parent.initializeEager(key);
		}

		@Override
		public void start() {
			parent.start();
		}
	}

	private static class Root extends Registry {

		private final Set<Key<?>> eager;

		Root(Configuration configuration) {
			super(configuration);
			this.eager = new HashSet<>();
		}

		@Override
		public void initializeEager(Key<?> key) {
			eager.add(Require.nonNull(key));
		}

		@Override
		public void start() {
			eager.forEach(this::get);
		}
	}

	public static Registry create(Configuration configuration) {
		return new Root(configuration);
	}

	private final Configuration configuration;
	private final Map<String, Registry> children;
	private final Map<Key<?>, ServiceFactory<?>> factories;
	@SuppressWarnings("rawtypes")
	private final Cache.Aside<Key, Object> instances;

	protected Registry(Configuration configuration) {
		this.configuration = Require.nonNull(configuration);
		this.children = new HashMap<>();
		this.factories = new HashMap<>();
		this.instances = Cache.sharedOnEqualKey();
	}

	@Override
	public <T> void bind(Key<T> key, ServiceFactory<? extends T> factory, Key<?>... path) {
		@SuppressWarnings("resource")
		Registry current = this;
		for (Key<?> target : path) {
			current = current.children.compute(target.name(), (n, r) -> {
				if (r == null) {
					return new Child(config(target), this);
				} else {
					throw new IllegalStateException(n + " already registered.");
				}
			});
		}
		current.factories.put(key, factory);
	}

	@Override
	public Registry child(Named value) {
		Registry child = children.get(value.name());
		return child != null ? child : new Child(config(value), this);
	}

	@Override
	public void close() {
		instances.evictAll(this::destroyService);
		children.values().forEach(Context::close);
	}

	private Configuration config(Named value) {
		return configuration.prefixed(value);
	}

	private <T> T createService(Key<T> key) throws Exception {
		Registry child = child(key);
		return factory(key).create(child, child.configuration);
	}

	private <T> void destroyService(Key<T> key, T service) throws Exception {
		factory(key).destroy(service);
	}

	private <T> ServiceFactory<T> factory(Key<T> key) {
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
		return (T) instances.acquire(key, this::createService);
	}

	protected boolean hasRegisteredFactory(Key<?> key) {
		return factories.containsKey(key);
	}

	public abstract void start();
}