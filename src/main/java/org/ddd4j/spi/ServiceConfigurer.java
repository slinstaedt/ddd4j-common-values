package org.ddd4j.spi;

import java.util.function.Consumer;

import org.ddd4j.value.Named;
import org.ddd4j.value.Typed;

public interface ServiceConfigurer {

	interface Registered<C extends Named> extends ServiceConfigurer, Typed<C>, Named {

		@Override
		default void bindServices(ServiceBinder binder) {
			binder.initializeEager(Key.of(getName(), ctx -> {
				Class<? extends C> type = getType().getRawType();
				ctx.get(ContextProvisioning.KEY).loadRegistered(type).forEach(r -> configurer(ctx.child(r)).accept(r));
				return new Object();
			}));
		}

		Consumer<C> configurer(Context context);
	}

	void bindServices(ServiceBinder binder);
}
