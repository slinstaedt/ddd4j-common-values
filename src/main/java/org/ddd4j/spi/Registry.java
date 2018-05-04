package org.ddd4j.spi;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.ddd4j.util.Require;
import org.ddd4j.util.Throwing;
import org.ddd4j.util.collection.Cache;
import org.ddd4j.util.value.Named;
import org.ddd4j.value.config.Configuration;

public interface Registry extends Context, ServiceBinder, AutoCloseable {

	abstract class Bound implements Context, ServiceBinder, AutoCloseable {

		private final Configuration configuration;
		private final Map<String, BoundChild> children;
		private final Map<Ref<?>, Set<ServiceFactory<?>>> bound;
		@SuppressWarnings("rawtypes")
		private final Cache.Aside<Ref, Object> instances;

		protected Bound(Configuration configuration) {
			this.configuration = Require.nonNull(configuration);
			this.children = new HashMap<>();
			this.bound = new HashMap<>();
			this.instances = Cache.sharedOnEqualKey();
		}

		@Override
		public <T> void bind(Ref<T> ref, ServiceFactory<? extends T> factory, Ref<?>... path) {
			@SuppressWarnings("resource")
			Bound current = this;
			for (Ref<?> target : path) {
				current = current.boundChild(target);
			}
			current.bound.computeIfAbsent(ref, k -> new HashSet<>()).add(Require.nonNull(factory));
		}

		private BoundChild boundChild(Named value) {
			return children.computeIfAbsent(value.name(), n -> new BoundChild(configuration.prefixed(n), this));
		}

		@SuppressWarnings("unchecked")
		protected <T> Set<ServiceFactory<T>> boundFactories(Ref<T> ref) {
			Set<?> entries = bound.getOrDefault(ref, Collections.emptySet());
			return (Set<ServiceFactory<T>>) entries;
		}

		private <T> ServiceFactory<T> boundFactory(Ref<T> ref) {
			Set<ServiceFactory<T>> factories = boundFactories(ref);
			switch (factories.size()) {
			case 0:
				return ref;
			case 1:
				return factories.iterator().next();
			default:
				throw new IllegalStateException("Multiple factories found in classpath for " + ref
						+ ". Either strip your classpath or select one implementation via configration.");
			}
		}

		@Override
		public Context child(Named value) {
			Context child = children.get(value.name());
			if (child == null) {
				if (configuration.getString(value.name()).isPresent()) {
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

		private <T> Optional<ServiceFactory<T>> configuredFactory(Ref<T> ref) {
			return configuration.getString(ref.name())
					.map(s -> s.isEmpty() ? null : s)
					.map(Throwing.TFunction.of(Class::forName))
					.map(Throwing.TFunction.of(c -> newInstance(c, ref)))
					.map(ServiceFactory.class::cast);
		}

		private <T> T createService(Ref<T> ref) throws Exception {
			return factory(ref).create(child(ref));
		}

		private <T> void destroyService(Ref<T> ref, T service) throws Exception {
			factory(ref).destroy(service);
		}

		private <T> ServiceFactory<T> factory(Ref<T> ref) {
			return configuredFactory(ref).orElseGet(() -> boundFactory(ref));
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> T get(Ref<T> ref) {
			return (T) instances.acquire(ref, this::createService);
		}

		private Object newInstance(Class<?> type, Named value) throws ReflectiveOperationException {
			try {
				return type.getConstructor(Context.class).newInstance(transientChild(value));
			} catch (NoSuchMethodException e) {
				return type.getConstructor().newInstance();
			}
		}

		private Context transientChild(Named value) {
			return new TransientChild(configuration.prefixed(value.name()), this);
		}
	}

	class BoundChild extends Bound {

		private final Bound parent;

		BoundChild(Configuration configuration, Bound parent) {
			super(configuration);
			this.parent = Require.nonNull(parent);
		}

		@Override
		public <T> T get(Ref<T> ref) {
			if (boundFactories(ref).isEmpty()) {
				return parent.get(ref);
			} else {
				return super.get(ref);
			}
		}

		@Override
		public void initializeEager(Ref<?> ref) {
			parent.initializeEager(ref);
		}

		@Override
		public <T extends Named> Optional<T> specific(Ref<T> ref, String name) {
			return parent.specific(ref, name);
		}
	}

	class Root extends Bound implements Registry {

		private final Set<Ref<?>> eager;
		private Cache.Aside<Ref<?>, Map<String, Object>> named;

		Root(Configuration configuration) {
			super(configuration);
			this.eager = new HashSet<>();
		}

		@Override
		public void initializeEager(Ref<?> ref) {
			eager.add(Require.nonNull(ref));
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T extends Named> Optional<T> specific(Ref<T> ref, String name) {
			Context child = child(ref);
			T service = (T) named.acquire(ref, k -> boundFactories(ref).stream().map(f -> f.createUnchecked(child)).collect(
					Collectors.toMap(Named::name, Function.identity()))).get(name);
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
		public <T> T get(Ref<T> ref) {
			return parent.get(ref);
		}

		@Override
		public <T extends Named> Optional<T> specific(Ref<T> ref, String name) {
			return parent.specific(ref, name);
		}
	}

	static Registry create(Configuration configuration) {
		return new Root(configuration);
	}

	@Override
	void close();

	Registry start();
}