package org.ddd4j.spi.java;

import java.util.Properties;

import org.ddd4j.spi.ServiceProvider;
import org.ddd4j.value.collection.Configuration;
import org.ddd4j.value.collection.Props;

public class SystemPropertiesConfigurationLoader implements Configuration.ConfigurationLoader {

	@Override
	public Configuration loadFor(ServiceProvider<?> provider) {
		Properties properties = System.getProperties();
		return new Props(properties);
	}
}
