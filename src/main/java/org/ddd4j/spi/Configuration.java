package org.ddd4j.spi;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.io.Output;
import org.ddd4j.value.Value;

public class Configuration extends Value.Simple<Configuration> {

	public static class Key<T> {

		private final String value;
		private final Function<? super String, ? extends T> converter;
		private final T defaultValue;

		public Key(String value, Function<? super String, ? extends T> converter, T defaultValue) {
			this.value = Require.nonEmpty(value);
			this.converter = Require.nonNull(converter);
			this.defaultValue = defaultValue;
		}

		public T convert(String value) {
			return value != null ? converter.apply(value) : Require.nonNull(defaultValue);
		}
	}

	public static final Configuration NONE = new Configuration();

	public static Key<Boolean> keyOfBoolean(String key, Boolean defaultValue) {
		return new Key<>(key, Boolean::valueOf, defaultValue);
	}

	public static Key<Integer> keyOfInteger(String key, Integer defaultValue) {
		return new Key<>(key, Integer::valueOf, defaultValue);
	}

	public static Key<Long> keyOfLong(String key, Long defaultValue) {
		return new Key<>(key, Long::valueOf, defaultValue);
	}

	public static <E extends Enum<E>> Key<E> keyOfEnum(Class<E> enumType, String key, E defaultValue) {
		return new Key<>(key, s -> Enum.valueOf(enumType, s), defaultValue);
	}

	private final Properties properties;

	public Configuration() {
		this.properties = new Properties();
	}

	public <T> T get(Key<T> key) {
		return key.convert(properties.getProperty(key.value));
	}

	public Optional<String> getString(String key) {
		return Optional.ofNullable(properties.getProperty(key));
	}

	public String getString(String key, String defaultValue) {
		return properties.getProperty(key, defaultValue);
	}

	public Optional<Boolean> getBoolean(String key) {
		return getString(key).map(Boolean::valueOf);
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		return getBoolean(key).orElse(defaultValue);
	}

	public Optional<Integer> getInteger(String key) {
		return getString(key).map(Integer::valueOf);
	}

	public int getInteger(String key, int defaultValue) {
		return getInteger(key).orElse(defaultValue);
	}

	public Optional<Long> getLong(String key) {
		return getString(key).map(Long::valueOf);
	}

	public long getLong(String key, long defaultValue) {
		return getLong(key).orElse(defaultValue);
	}

	public <E extends Enum<E>> Optional<E> getEnum(Class<E> enumType, String key) {
		return getString(key).map(s -> Enum.valueOf(enumType, s));
	}

	public <E extends Enum<E>> E getEnum(Class<E> enumType, String key, E defaultValue) {
		return getEnum(enumType, key).orElse(defaultValue);
	}

	@Override
	public void serialize(Output output) throws IOException {
		// TODO magic value?
		properties.store(output.asStream(), null);
	}

	@Override
	protected Object value() {
		return properties;
	}
}
