package org.ddd4j.spi;

import org.ddd4j.value.Type;
import org.ddd4j.value.collection.Seq;

@FunctionalInterface
public interface ServiceProvider<S extends Service<S, ?>> {

	@FunctionalInterface
	interface Loader {

		<P extends ServiceProvider<?>> Seq<P> load(Type<P> providerType);
	}

	S provideService(Configuration configuration, ServiceLocator locator);
}
