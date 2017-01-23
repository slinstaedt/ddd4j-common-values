package org.ddd4j.value.collection;

import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Properties;

import org.ddd4j.contract.Require;
import org.ddd4j.spi.ServiceProvider;
import org.ddd4j.value.Throwing.TFunction;
import org.ddd4j.value.Value;

public interface Configuration extends Value<Configuration> {

	interface Key<V> {

		static Key<String> of(String key, String defaultValue) {
			Require.nonEmpty(key);
			return new Key<String>() {

				@Override
				public Configuration toConfig(Configuration configuration, String value) {
					return configuration.with(key, value);
				}

				@Override
				public String valueOf(Configuration configuration) {
					return configuration.getString(key).orElse(defaultValue);
				}
			};
		}

		static <V> Key<V> ofStringable(String key, V defaultValue, TFunction<String, V> converter) {
			Require.nonNullElements(key, defaultValue, converter);
			return new Key<V>() {

				@Override
				public Configuration toConfig(Configuration configuration, V value) {
					return configuration.with(key, value.toString());
				}

				@Override
				public V valueOf(Configuration configuration) {
					return configuration.getString(key).map(converter).orElse(defaultValue);
				}
			};
		}

		default <X> Key<X> map(TFunction<? super V, ? extends X> reader, TFunction<? super X, ? extends V> writer) {
			Require.nonNullElements(reader, writer);
			return new Key<X>() {

				@Override
				public Configuration toConfig(Configuration configuration, X value) {
					return Key.this.toConfig(configuration, writer.apply(value));
				}

				@Override
				public X valueOf(Configuration configuration) {
					return reader.apply(Key.this.valueOf(configuration));
				}
			};
		}

		Configuration toConfig(Configuration configuration, V value);

		V valueOf(Configuration configuration);

		default Key<V> withDefault(V value) {
			Require.nonNull(value);
			return new Key<V>() {

				@Override
				public Configuration toConfig(Configuration configuration, V value) {
					return Key.this.toConfig(configuration, value);
				}

				@Override
				public V valueOf(Configuration configuration) {
					V v = Key.this.valueOf(configuration);
					return v != null ? v : value;
				}
			};
		}
	}

	@FunctionalInterface
	interface Loader {

		Configuration loadFor(ServiceProvider<?> provider);
	}

	String KEY_DELIMITER = ".";

	Configuration NONE = k -> Optional.empty();

	static Class<?> classForName(String className) {
		return Class.forName(className);
	}

	static Key<Boolean> keyOfBoolean(String key, Boolean defaultValue) {
		return Key.ofStringable(key, defaultValue, Boolean::valueOf);
	}

	static Key<Class<?>> keyOfClass(String key, Class<?> defaultValue) {
		return Key.ofStringable(key, defaultValue, Class::forName);
	}

	static <E extends Enum<E>> Key<E> keyOfEnum(Class<E> enumType, String key, E defaultValue) {
		return Key.ofStringable(key, defaultValue, s -> Enum.valueOf(enumType, s));
	}

	static Key<Integer> keyOfInteger(String key, Integer defaultValue) {
		return Key.ofStringable(key, defaultValue, Integer::valueOf);
	}

	static Key<LocalDate> keyOfLocalDate(String key, LocalDate defaultValue) {
		return Key.ofStringable(key, defaultValue, LocalDate::parse);
	}

	static Key<LocalDateTime> keyOfLocalDateTime(String key, LocalDateTime defaultValue) {
		return Key.ofStringable(key, defaultValue, LocalDateTime::parse);
	}

	static Key<Long> keyOfLong(String key, Long defaultValue) {
		return Key.ofStringable(key, defaultValue, Long::valueOf);
	}

	static Key<URI> keyOfURI(String key, URI defaultValue) {
		return Key.ofStringable(key, defaultValue, URI::new);
	}

	static Key<URL> keyOfURL(String key, URL defaultValue) {
		return Key.ofStringable(key, defaultValue, URL::new);
	}

	default Configuration fallbackTo(Configuration fallback) {
		Require.nonNull(fallback);
		return new Configuration() {

			@Override
			public Seq<Tpl<String, String>> entries() {
				return Configuration.this.entries();
			}

			@Override
			public Optional<String> getString(String key) {
				Optional<String> value = Configuration.this.getString(key);
				return value.isPresent() ? value : fallback.getString(key);
			}

			@Override
			public Configuration with(String key, String value) {
				return Configuration.this.with(key, value).fallbackTo(fallback);
			}
		};
	}

	default <V> V get(Key<V> key) {
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
		return getString(key).map(Configuration::classForName).map(c -> c.<X>asSubclass(baseType));
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

	Seq<Tpl<String, String>> entries();

	default String getString(String key, String defaultValue) {
		return getString(key).orElse(defaultValue);
	}

	default Configuration prefixed(String keyPrefix) {
		Require.nonEmpty(keyPrefix);
		return new Configuration() {

			@Override
			public Seq<Tpl<String, String>> entries() {
				return Configuration.this.entries().map().to(tpl -> tpl.mapLeft(keyPrefix::concat));
			}

			@Override
			public Optional<String> getString(String key) {
				return Configuration.this.getString(keyPrefix + KEY_DELIMITER + key);
			}

			@Override
			public Configuration with(String key, String value) {
				return Configuration.this.with(keyPrefix + KEY_DELIMITER + key, value);
			}
		};
	}

	default <V> Configuration with(Key<V> key, V value) {
		return key.toConfig(this, value);
	}

	default Configuration with(String key, String value) {
		throw new UnsupportedOperationException();
	}
}
