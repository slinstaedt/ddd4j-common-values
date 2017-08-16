package org.ddd4j.value.config;

import org.ddd4j.Require;
import org.ddd4j.Throwing.TFunction;

public interface ConfKey<V> {

	static ConfKey<String> of(String key, String defaultValue) {
		Require.nonEmpty(key);
		return c -> c.getString(key).orElse(defaultValue);
	}

	static <V> ConfKey<V> ofStringable(String key, V defaultValue, TFunction<String, V> converter) {
		Require.nonNullElements(key, defaultValue, converter);
		return c -> c.getString(key).map(converter).orElse(defaultValue);
	}

	default <X> ConfKey<X> map(TFunction<? super V, ? extends X> mapper) {
		Require.nonNull(mapper);
		return c -> mapper.apply(valueOf(c));
	}

	V valueOf(Configuration configuration);
}