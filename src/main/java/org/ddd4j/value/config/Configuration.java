package org.ddd4j.value.config;

import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Properties;

import org.ddd4j.Require;
import org.ddd4j.value.Type;

public interface Configuration {

	String KEY_DELIMITER = ".";

	static ConfKey<Boolean> keyOfBoolean(String key, Boolean defaultValue) {
		return ConfKey.ofStringable(key, defaultValue, Boolean::valueOf);
	}

	static <X> ConfKey<Class<? extends X>> keyOfClass(String key, Class<X> baseClass, Class<? extends X> defaultValue) {
		return ConfKey.ofStringable(key, Type.of(defaultValue), Type::forName).map(t -> t.asSubType(baseClass).getRawType());
	}

	static <E extends Enum<E>> ConfKey<E> keyOfEnum(Class<E> enumType, String key, E defaultValue) {
		return ConfKey.ofStringable(key, defaultValue, s -> Enum.valueOf(enumType, s));
	}

	static ConfKey<Integer> keyOfInteger(String key, Integer defaultValue) {
		return ConfKey.ofStringable(key, defaultValue, Integer::valueOf);
	}

	static ConfKey<LocalDate> keyOfLocalDate(String key, LocalDate defaultValue) {
		return ConfKey.ofStringable(key, defaultValue, LocalDate::parse);
	}

	static ConfKey<LocalDateTime> keyOfLocalDateTime(String key, LocalDateTime defaultValue) {
		return ConfKey.ofStringable(key, defaultValue, LocalDateTime::parse);
	}

	static ConfKey<Long> keyOfLong(String key, Long defaultValue) {
		return ConfKey.ofStringable(key, defaultValue, Long::valueOf);
	}

	static ConfKey<URI> keyOfURI(String key, URI defaultValue) {
		return ConfKey.ofStringable(key, defaultValue, URI::new);
	}

	static ConfKey<URL> keyOfURL(String key, URL defaultValue) {
		return ConfKey.ofStringable(key, defaultValue, URL::new);
	}

	default <V> V get(ConfKey<V> key) {
		return key.valueOf(this);
	}

	default Properties getPropertiesOf(String... keys) {
		Properties properties = new Properties();
		for (String key : keys) {
			getString(key).ifPresent(v -> properties.setProperty(key, v));
		}
		return properties;
	}

	default Optional<Boolean> getBoolean(String key) {
		return getString(key).map(Boolean::valueOf);
	}

	default <X> Optional<Class<? extends X>> getClass(Class<X> baseType, String key) {
		return getString(key).map(Type::forName).map(t -> t.asSubType(baseType).getRawType());
	}

	default <E extends Enum<E>> Optional<E> getEnum(Class<E> enumType, String key) {
		return getString(key).map(s -> Enum.valueOf(enumType, s));
	}

	default Optional<Integer> getInteger(String key) {
		return getString(key).map(Integer::valueOf);
	}

	default Optional<Long> getLong(String key) {
		return getString(key).map(Long::valueOf);
	}

	Optional<String> getString(String key);

	default Configuration prefixed(String keyPrefix) {
		Require.nonEmpty(keyPrefix);
		return key -> {
			Optional<String> prefixed = Configuration.this.getString(keyPrefix + KEY_DELIMITER + key);
			return prefixed.isPresent() ? prefixed : Configuration.this.getString(key);
		};
	}
}
