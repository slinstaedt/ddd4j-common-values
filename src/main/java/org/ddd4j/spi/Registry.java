package org.ddd4j.spi;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
import org.ddd4j.collection.Cache;
import org.ddd4j.value.Named;
import org.ddd4j.value.config.Configuration;

public interface Registry extends Context, ServiceBinder, AutoCloseable {

	abstract class Bound implements Context, ServiceBinder, AutoCloseable {

		private final Configuration configuration;
		private final Map<String, BoundChild> children;
		private final Map<Key<?>, Set<ServiceFactory<?>>> bound;
		@SuppressWarnings("rawtypes")
		private final Cache.Aside<Key, Object> instances;

		protected Bound(Configuration configuration) {
			this.configuration = Require.nonNull(configuration);
			this.children = new HashMap<>();
			this.bound = new HashMap<>();
			this.instances = Cache.sharedOnEqualKey();
		}

		@Override
		public <T> void bind(Key<T> key, ServiceFactory<? extends T> factory, Key<?>... path) {
			@SuppressWarnings("resource")
			Bound current = this;
			for (Key<?> target : path) {
				current = current.boundChild(target);
			}
			current.bound.computeIfAbsent(key, k -> new HashSet<>()).add(Require.nonNull(factory));
		}

		private BoundChild boundChild(Named value) {
			return children.computeIfAbsent(value.getName(), n -> new BoundChild(configuration.prefixed(n), this));
		}

		@SuppressWarnings("unchecked")
		protected <T> Set<ServiceFactory<T>> boundFactories(Key<T> key) {
			Set<?> entries = bound.getOrDefault(key, Collections.emptySet());
			return (Set<ServiceFactory<T>>) entries;
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
		public Context child(Named value) {
			Context child = children.get(value.getName());
			if (child == null) {
				if (configuration.getString(value.getName()).isPresent()) {
					child = boundChild(value);
				} else {
					child = transientChild(value);
				}
			}
			return child;
		}

		@Override
		public void close() {
			children.values().forEach(Bound::close);
			instances.evictAll(this::destroyService);
		}

		@Override
		public Configuration configuration() {
			return configuration;
		}

		private <T> Optional<ServiceFactory<T>> configuredFactory(Key<T> key) {
			return configuration.getString(key.getName())
					.map(s -> s.isEmpty() ? null : s)
					.map(Throwing.TFunction.of(Class::forName))
					.map(Throwing.TFunction.of(c -> newInstance(c, key)))
					.map(ServiceFactory.class::cast);
		}

		private <T> T createService(Key<T> key) throws Exception {
			return factory(key).create(child(key));
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

		private Object newInstance(Class<?> type, Named value) throws ReflectiveOperationException {
			try {
				return type.getConstructor(Context.class).newInstance(transientChild(value));
			} catch (NoSuchMethodException e) {
				return type.newInstance();
			}
		}

		private Context transientChild(Named value) {
			return new TransientChild(configuration.prefixed(value.getName()), this);
		}
	}

	class BoundChild extends Bound {

		private final Bound parent;

		BoundChild(Configuration configuration, Bound parent) {
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
		public <T extends Named> Optional<T> specific(Key<T> key, String name) {
			return parent.specific(key, name);
		}
	}

	class Root extends Bound implements Registry {

		private final Set<Key<?>> eager;
		private Cache.Aside<Key<?>, Map<String, Object>> named;

		Root(Configuration configuration) {
			super(configuration);
			this.eager = new HashSet<>();
		}

		@Override
		public void initializeEager(Key<?> key) {
			eager.add(Require.nonNull(key));
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T extends Named> Optional<T> specific(Key<T> key, String name) {
			Context child = child(key);
			T service = (T) named.acquire(key, k -> boundFactories(key).stream().map(f -> f.createUnchecked(child)).collect(
					Collectors.toMap(Named::getName, Function.identity()))).get(name);
			return Optional.ofNullable(service);
		}

		@Override
		public Registry start() {
			eager.forEach(this::get);
			return this;
		}
	}

	class TransientChild implements Context {

		private final Configuration configuration;
		private final Bound parent;

		TransientChild(Configuration configuration, Bound parent) {
			this.configuration = Require.nonNull(configuration);
			this.parent = Require.nonNull(parent);
		}

		@Override
		public Context child(Named value) {
			return parent.child(value);
		}

		@Override
		public Configuration configuration() {
			return configuration;
		}

		@Override
		public <T> T get(Key<T> key) {
			return parent.get(key);
		}

		@Override
		public <T extends Named> Optional<T> specific(Key<T> key, String name) {
			return parent.specific(key, name);
		}
	}

	static Registry create(Configuration configuration) {
		return new Root(configuration);
	}

	@Override
	void close();

	Registry start();
}