package org.ddd4j.spi;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
import org.ddd4j.Throwing.TConsumer;

@FunctionalInterface
public interface ServiceFactory<T> {

	T create(Context context) throws Exception;

	default T createUnchecked(Context context) {
		try {
			return create(context);
		} catch (Exception e) {
			return Throwing.unchecked(e);
		}
	}

	default void destroy(T service) throws Exception {
	}

	default ServiceFactory<T> withDestructor(TConsumer<? super T> destructor) {
		Require.nonNull(destructor);
		return new ServiceFactory<T>() {

			@Override
			public T create(Context context) throws Exception {
				return ServiceFactory.this.create(context);
			}

			@Override
			public void destroy(T service) throws Exception {
				destructor.acceptChecked(service);
			}
		};
	}
}
