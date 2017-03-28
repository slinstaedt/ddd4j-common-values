package org.ddd4j.spi;

import org.ddd4j.Require;
import org.ddd4j.Throwing.TConsumer;
import org.ddd4j.value.collection.Configuration;

@FunctionalInterface
public interface ServiceFactory<T> {

	T create(Context context, Configuration configuration) throws Exception;

	default void destroy(T service) throws Exception {
	}

	default ServiceFactory<T> withDestructor(TConsumer<? super T> destructor) {
		Require.nonNull(destructor);
		return new ServiceFactory<T>() {

			@Override
			public T create(Context context, Configuration configuration) throws Exception {
				return ServiceFactory.this.create(context, configuration);
			}

			@Override
			public void destroy(T service) throws Exception {
				destructor.acceptChecked(service);
			}
		};
	}
}
