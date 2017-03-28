package org.ddd4j.spi;

import org.ddd4j.Require;

public interface ServiceBinder {

	interface ScopedBinder<T> extends TargetBinder<T> {

		void bind(ServiceFactory<? extends T> factory, Key<?>... targets);

		default TargetBinder<T> of(Key<?>... targets) {
			Require.nonNull(targets);
			return f -> bind(f, targets);
		}

		@Override
		default void toFactory(ServiceFactory<? extends T> factory) {
			bind(factory);
		}
	}

	interface TargetBinder<T> {

		default void toDelegate(Key<? extends T> delegationKey) {
			Require.nonNull(delegationKey);
			toFactory((ctx, conf) -> ctx.get(delegationKey));
		}

		void toFactory(ServiceFactory<? extends T> factory);

		default void toInstance(T instance) {
			Require.nonNull(instance);
			toFactory((ctx, conf) -> instance);
		}
	}

	default <T> ScopedBinder<T> bind(Key<T> key) {
		Require.nonNull(key);
		return (f, t) -> bind(key, f, t);
	}

	default void accept(ServiceProvider provider) {
		provider.bindServices(this);
	}

	<T> void bind(Key<T> key, ServiceFactory<? extends T> factory, Key<?>... dependencyPath);

	void initializeEager(Key<?> key);
}
