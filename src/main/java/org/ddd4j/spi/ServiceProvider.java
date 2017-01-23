package org.ddd4j.spi;

import org.ddd4j.value.collection.Configuration;

@FunctionalInterface
public interface ServiceProvider<S extends Service<S, ?>> extends Named {

	S provideService(Configuration configuration, ServiceLocator locator);
}
