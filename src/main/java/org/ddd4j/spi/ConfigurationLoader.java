package org.ddd4j.spi;

import org.ddd4j.value.collection.Configuration;

@FunctionalInterface
interface ConfigurationLoader {

	Configuration loadFor(ServiceProvider<?> provider);
}