package org.ddd4j.spi;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
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
			if (boundFactories(key).isEmpty()) {
				return parent.get(key);
			} else {
				return super.get(key);
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
	private final Map<Key<?>, Set<ServiceFactory<?>>> bound;
	@SuppressWarnings("rawtypes")
	private final Cache.Aside<Key, Object> instances;

	protected Registry(Configuration configuration) {
		this.configuration = Require.nonNull(configuration);
		this.children = new HashMap<>();
		this.bound = new HashMap<>();
		this.instances = Cache.sharedOnEqualKey();
	}

	private Registry addChild(Named value) {
		return children.computeIfAbsent(value.name(), n -> new Child(configuration.prefixed(n), this));
	}

	@Override
	public <T> void bind(Key<T> key, ServiceFactory<? extends T> factory, Key<?>... path) {
		@SuppressWarnings("resource")
		Registry current = this;
		for (Key<?> target : path) {
			current = current.addChild(target);
		}
		current.bound.computeIfAbsent(key, k -> new HashSet<>()).add(Require.nonNull(factory));
	}

	@SuppressWarnings("unchecked")
	protected <T> Set<ServiceFactory<T>> boundFactories(Key<?> key) {
		Set<?> set = bound.getOrDefault(key, Collections.emptySet());
		return (Set<ServiceFactory<T>>) set;
	}

	private <T> ServiceFactory<T> boundFactory(Key<T> key) {
		Set<ServiceFactory<T>> factories = boundFactories(key);
		switch (factories.size()) {
		case 0:
			return key;
		case 1:
			return factories.iterator().next();
		default:
			throw new IllegalStateException("Multiple factories found in classpath for " + key
					+ ". Either strip your classpath or select one implementation via configration.");
		}
	}

	@Override
	public Registry child(Named value) {
		Registry child = children.get(value.name());
		if (child == null) {
			if (configuration.getString(value.name()).isPresent()) {
				child = addChild(value);
			} else {
				child = new Child(configuration.prefixed(value), this);
			}
		}
		return child;
	}

	@Override
	public void close() {
		children.values().forEach(Context::close);
		instances.evictAll(this::destroyService);
	}

	@Override
	public Configuration configuration() {
		return configuration;
	}

	private <T> Optional<ServiceFactory<T>> configuredFactory(Key<T> key) {
		return configuration.getString(key.name())
				.map(s -> s.isEmpty() ? null : s)
				.map(Throwing.applied(Class::forName))
				.map(Throwing.applied(Class::newInstance))
				.map(ServiceFactory.class::cast);
	}

	private <T> T createService(Key<T> key) throws Exception {
		Registry child = child(key);
		return factory(key).create(child);
	}

	private <T> void destroyService(Key<T> key, T service) throws Exception {
		factory(key).destroy(service);
	}

	private <T> ServiceFactory<T> factory(Key<T> key) {
		return configuredFactory(key).orElseGet(() -> boundFactory(key));
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T get(Key<T> key) {
		return (T) instances.acquire(key, this::createService);
	}

	public abstract void start();
}