package org.ddd4j.infrastructure.registry.local;

import org.ddd4j.collection.Cache;
import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.registry.Registry;
import org.ddd4j.infrastructure.registry.RegistryKey;
import org.ddd4j.spi.Configuration;
import org.ddd4j.spi.ServiceLocator;

public class LocalRegistry implements Registry {

	private final Cache.ReadThrough<RegistryKey<?>, ?> cache;

	public LocalRegistry(Configuration configuration, ServiceLocator locator) {
		Require.nonNullElements(configuration, locator);
		this.cache = Cache.<RegistryKey<?>, Object> sharedOnEqualKey().withFactory(k -> k.create(configuration, locator));
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T lookup(RegistryKey<T> key) {
		return (T) cache.acquire(key);
	}
}
