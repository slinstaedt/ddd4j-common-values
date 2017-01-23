package org.ddd4j.spi.java;

import java.util.Optional;
import java.util.Properties;

import org.ddd4j.value.collection.Configuration;

public class PropertiesConfiguration implements Configuration {

	private final Properties properties;

	public PropertiesConfiguration(Properties properties) {
		this.properties = new Properties();
		this.properties.putAll(properties);
	}

	private PropertiesConfiguration(Properties copy, String key, String value) {
		this.properties = new Properties(copy);
		this.properties.put(key, value);
	}

	@Override
	public Optional<String> getString(String key) {
		return Optional.ofNullable(properties.getProperty(key));
	}

	@Override
	public Configuration with(String key, String value) {
		return new PropertiesConfiguration(properties, key, value);
	}
}
