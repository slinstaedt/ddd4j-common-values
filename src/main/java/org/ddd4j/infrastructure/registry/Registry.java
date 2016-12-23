package org.ddd4j.infrastructure.registry;

import org.ddd4j.spi.Service;

public interface Registry extends Service<Registry, RegistryProvider> {

	<T> T lookup(RegistryKey<T> key);
}
