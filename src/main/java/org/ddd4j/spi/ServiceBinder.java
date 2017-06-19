package org.ddd4j.spi;

import org.ddd4j.Require;

public interface ServiceBinder {

	interface ScopedBinder<T> extends TargetBinder<T> {

		void bind(ServiceFactory<? extends T> factory, Key<?>... path);

		default TargetBinder<T> of(Key<?>... path) {
			Require.nonNull(path);
			return f -> bind(f, path);
		}

		@Override
		default void toFactory(ServiceFactory<? extends T> factory) {
			bind(factory);
		}
	}

	interface TargetBinder<T> {

		default void toDelegate(Key<? extends T> delegationKey) {
			Require.nonNull(delegationKey);
			toFactory(ctx -> ctx.get(delegationKey));
		}

		void toFactory(ServiceFactory<? extends T> factory);

		default void toInstance(T instance) {
			Require.nonNull(instance);
			toFactory(ctx -> instance);
		}
	}

	default <T> ScopedBinder<T> bind(Key<T> key) {
		Require.nonNull(key);
		return (f, p) -> bind(key, f, p);
	}

	default void accept(ServiceConfigurer configurer) {
		configurer.bindServices(this);
	}

	<T> void bind(Key<T> key, ServiceFactory<? extends T> factory, Key<?>... dependencyPath);

	void initializeEager(Key<?> key);
}
