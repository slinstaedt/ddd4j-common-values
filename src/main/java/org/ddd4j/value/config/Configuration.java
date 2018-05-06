package org.ddd4j.value.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Properties;

import org.ddd4j.util.Require;
import org.ddd4j.util.Type;

public interface Configuration {

	String KEY_DELIMITER = ".";

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

	default Optional<LocalDate> getLocalDate(String key) {
		return getString(key).map(LocalDate::parse);
	}

	default Optional<LocalDateTime> getLocalDateTime(String key) {
		return getString(key).map(LocalDateTime::parse);
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

	default Properties propertiesOf(String... keys) {
		Properties properties = new Properties();
		for (String key : keys) {
			getString(key).ifPresent(v -> properties.setProperty(key, v));
		}
		return properties;
	}
}
