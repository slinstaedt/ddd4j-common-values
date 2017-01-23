package org.ddd4j.infrastructure.registry.local;

import org.ddd4j.infrastructure.registry.Registry;
import org.ddd4j.infrastructure.registry.RegistryProvider;
import org.ddd4j.spi.ServiceLocator;
import org.ddd4j.value.collection.Configuration;

public class LocalRegistryProvider implements RegistryProvider {

	@Override
	public Registry provideService(Configuration configuration, ServiceLocator locator) {
		return new LocalRegistry(configuration, locator);
	}
}
