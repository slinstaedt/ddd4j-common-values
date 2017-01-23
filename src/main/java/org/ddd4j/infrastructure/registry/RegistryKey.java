package org.ddd4j.infrastructure.registry;

import org.ddd4j.spi.ServiceLocator;
import org.ddd4j.value.collection.Configuration;

@FunctionalInterface
public interface RegistryKey<T> {

	T create(Configuration configuration, ServiceLocator locator);
}
