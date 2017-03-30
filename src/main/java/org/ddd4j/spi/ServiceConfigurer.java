package org.ddd4j.spi;

import java.util.function.Consumer;

import org.ddd4j.value.Named;
import org.ddd4j.value.Type;
import org.ddd4j.value.Type.Variable;

public interface ServiceConfigurer {

	interface Registered<R extends Named> extends ServiceConfigurer, Named {

		@Override
		default void bindServices(ServiceBinder binder) {
			binder.initializeEager(Key.of(name(), ctx -> {
				ctx.get(ContextProvisioning.KEY).loadRegistered(type()).forEach(r -> configurer(ctx.child(r)).accept(r));
				return new Object();
			}));
		}

		Consumer<R> configurer(Context context);

		default Class<? extends R> type() {
			Variable<Registered<R>, R> var = Type.variable(Registered.class, 0, Named.class);
			return Type.ofInstance(this).resolve(var).getRawType();
		}
	}

	void bindServices(ServiceBinder binder);
}
