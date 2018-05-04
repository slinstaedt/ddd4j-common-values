package org.ddd4j.spi;

import org.ddd4j.util.Typed;
import org.ddd4j.util.value.Named;

public interface ServiceConfigurer {

	interface Registered<C extends Named> extends ServiceConfigurer, Typed<C>, Named {

		@Override
		default void bindServices(ServiceBinder binder) {
			binder.initializeEager(Ref.of(name(), ctx -> {
				Class<? extends C> type = getType().getRawType();
				ctx.get(ContextProvisioning.REF).loadRegistered(type).forEach(c -> configure(ctx.child(c), c));
				return new Object();
			}));
		}

		void configure(Context context, C component);
	}

	void bindServices(ServiceBinder binder);
}
