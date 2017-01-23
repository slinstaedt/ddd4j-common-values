package org.ddd4j.spi;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import org.ddd4j.contract.Require;
import org.ddd4j.spi.java.JavaServiceProviderLoader;
import org.ddd4j.spi.java.SystemPropertiesConfigurationLoader;
import org.ddd4j.value.collection.Configuration;

public class Providers {

	public static ServiceLocator createServiceLocator() {
		ServiceProviderLoader providerLoader = loadWithDefault(ServiceProviderLoader.class, JavaServiceProviderLoader::new);
		Configuration.ConfigurationLoader configurationLoader = loadWithDefault(Configuration.ConfigurationLoader.class, SystemPropertiesConfigurationLoader::new);
		return new ServiceLocator(providerLoader, configurationLoader);
	}

	public static <S> S loadWithDefault(Class<S> serviceType, Supplier<S> defaultImplementation) {
		Iterator<S> iterator = ServiceLoader.load(serviceType).iterator();
		if (iterator.hasNext()) {
			S service = iterator.next();
			if (iterator.hasNext()) {
				throw new IllegalStateException("More than one service configured for: " + serviceType.getTypeName());
			}
			return Require.nonNull(service);
		} else if (defaultImplementation != null) {
			return Require.nonNull(defaultImplementation.get());
		} else {
			throw new IllegalStateException("No service configured for: " + serviceType.getTypeName());
		}
	}
}