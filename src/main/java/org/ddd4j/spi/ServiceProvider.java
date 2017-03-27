package org.ddd4j.spi;

import java.util.UUID;

import org.ddd4j.value.collection.Configuration;

public interface ServiceProvider {

	interface Eager extends ServiceProvider, Named {

		@Override
		default void bindServices(ServiceBinder binder) {
			binder.initializeEager(Key.of(name(), (ctx, conf) -> {
				initialize(ctx, conf);
				return UUID.randomUUID();
			}));
		}

		void initialize(Context context, Configuration configuration) throws Exception;
	}

	void bindServices(ServiceBinder binder);
}
