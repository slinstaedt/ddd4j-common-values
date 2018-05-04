package org.ddd4j.spi;

import org.ddd4j.util.Require;

public interface ServiceBinder {

	interface ScopedBinder<T> extends TargetBinder<T> {

		void bind(ServiceFactory<? extends T> factory, Ref<?>... path);

		default TargetBinder<T> of(Ref<?>... path) {
			Require.nonNull(path);
			return f -> bind(f, path);
		}

		@Override
		default void toFactory(ServiceFactory<? extends T> factory) {
			bind(factory);
		}
	}

	interface TargetBinder<T> {

		default void toDelegate(Ref<? extends T> delegate) {
			Require.nonNull(delegate);
			toFactory(ctx -> ctx.get(delegate));
		}

		void toFactory(ServiceFactory<? extends T> factory);

		default void toInstance(T instance) {
			Require.nonNull(instance);
			toFactory(ctx -> instance);
		}
	}

	default void accept(ServiceConfigurer configurer) {
		configurer.bindServices(this);
	}

	default <T> ScopedBinder<T> bind(Ref<T> ref) {
		Require.nonNull(ref);
		return (f, p) -> bind(ref, f, p);
	}

	<T> void bind(Ref<T> ref, ServiceFactory<? extends T> factory, Ref<?>... dependencyPath);

	void initializeEager(Ref<?> ref);
}
