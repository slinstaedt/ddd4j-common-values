package org.ddd4j.value.config;

import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.ddd4j.util.Require;
import org.ddd4j.util.Throwing.TFunction;

public interface ConfKey<V> {

	static <V> ConfKey<V> of(String key, V defaultValue, TFunction<String, V> converter) {
		Require.nonEmpty(key);
		Require.nonNull(converter);
		return c -> c.getString(key).map(converter).orElse(defaultValue);
	}

	static ConfKey<Boolean> ofBoolean(String key, Boolean defaultValue) {
		Require.nonEmpty(key);
		return c -> c.getBoolean(key).orElse(defaultValue);
	}

	static <X> ConfKey<Class<? extends X>> ofClass(String key, Class<X> baseClass, Class<? extends X> defaultValue) {
		Require.nonEmpty(key);
		Require.nonNull(baseClass);
		return c -> c.getClass(baseClass, key).orElse(defaultValue);
	}

	static <E extends Enum<E>> ConfKey<E> ofEnum(Class<E> enumType, String key, E defaultValue) {
		Require.nonEmpty(key);
		Require.nonNull(enumType);
		return c -> c.getEnum(enumType, key).orElse(defaultValue);
	}

	static ConfKey<Integer> ofInteger(String key, Integer defaultValue) {
		Require.nonEmpty(key);
		return c -> c.getInteger(key).orElse(defaultValue);
	}

	static ConfKey<LocalDate> ofLocalDate(String key, LocalDate defaultValue) {
		Require.nonEmpty(key);
		return c -> c.getLocalDate(key).orElse(defaultValue);
	}

	static ConfKey<LocalDateTime> ofLocalDateTime(String key, LocalDateTime defaultValue) {
		Require.nonEmpty(key);
		return c -> c.getLocalDateTime(key).orElse(defaultValue);
	}

	static ConfKey<Long> ofLong(String key, Long defaultValue) {
		Require.nonEmpty(key);
		return c -> c.getLong(key).orElse(defaultValue);
	}

	static ConfKey<String> ofString(String key, String defaultValue) {
		Require.nonEmpty(key);
		return c -> c.getString(key).orElse(defaultValue);
	}

	static ConfKey<URI> ofURI(String key, URI defaultValue) {
		return ConfKey.of(key, defaultValue, URI::new);
	}

	static ConfKey<URL> ofURL(String key, URL defaultValue) {
		return ConfKey.of(key, defaultValue, URL::new);
	}

	default <X> ConfKey<X> map(TFunction<? super V, ? extends X> mapper) {
		Require.nonNull(mapper);
		return c -> mapper.apply(valueOf(c));
	}

	V valueOf(Configuration configuration);
}