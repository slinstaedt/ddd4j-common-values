package org.ddd4j.spi.java;

import java.util.Properties;

import org.ddd4j.spi.Configuration;
import org.ddd4j.spi.ServiceProvider;

public class SystemPropertiesConfigurationLoader implements Configuration.Loader {

	@Override
	public Configuration loadFor(ServiceProvider<?> provider) {
		Properties properties = System.getProperties();
		return new PropertiesConfiguration(properties);
	}
}
