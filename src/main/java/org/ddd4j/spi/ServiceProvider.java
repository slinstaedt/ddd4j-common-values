package org.ddd4j.spi;

import java.util.function.Consumer;

import org.ddd4j.value.collection.Configuration;

public interface ServiceProvider {

	interface Registered<R extends Named> extends ServiceProvider, Named {

		@Override
		default void bindServices(ServiceBinder binder) {
			binder.initializeEager(Key.of(name(), (ctx, conf) -> {
				ctx.get(ContextProvisioning.KEY).loadRegistered(type()).forEach(r -> {
					Class<? extends Named> class1 = r.getClass();
				});
				return new Object();
			}));
		}

		Consumer<R> configurer(Context context, Configuration configuration);

		Class<? extends R> type();
	}

	void bindServices(ServiceBinder binder);
}
