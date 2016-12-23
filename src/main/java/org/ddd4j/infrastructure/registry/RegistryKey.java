package org.ddd4j.infrastructure.registry;

import org.ddd4j.spi.Configuration;
import org.ddd4j.spi.ServiceLocator;

@FunctionalInterface
public interface RegistryKey<T> {

	T create(Configuration configuration, ServiceLocator locator);
}
