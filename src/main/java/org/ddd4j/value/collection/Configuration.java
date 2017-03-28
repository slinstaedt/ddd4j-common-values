package org.ddd4j.value.collection;

import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.ddd4j.Throwing;
import org.ddd4j.Throwing.TFunction;
import org.ddd4j.contract.Require;
import org.ddd4j.value.Named;
import org.ddd4j.value.Value;

public interface Configuration extends Value<Configuration>, Seq<Tpl<String, String>> {

	interface Key<V> {

		static Key<String> of(String key, String defaultValue) {
			Require.nonEmpty(key);
			return new Key<String>() {

				@Override
				public <C extends Configuration> C toConfig(BiFunction<String, String, C> factory, String value) {
					return factory.apply(key, value);
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
				public <C extends Configuration> C toConfig(BiFunction<String, String, C> factory, V value) {
					return factory.apply(key, value.toString());
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
				public <C extends Configuration> C toConfig(BiFunction<String, String, C> factory, X value) {
					return Key.this.toConfig(factory, writer.apply(value));
				}

				@Override
				public X valueOf(Configuration configuration) {
					return reader.apply(Key.this.valueOf(configuration));
				}
			};
		}

		<C extends Configuration> C toConfig(BiFunction<String, String, C> factory, V value);

		V valueOf(Configuration configuration);

		default Key<V> withDefault(V value) {
			Require.nonNull(value);
			return new Key<V>() {

				@Override
				public <C extends Configuration> C toConfig(BiFunction<String, String, C> factory, V value) {
					return Key.this.toConfig(factory, value);
				}

				@Override
				public V valueOf(Configuration configuration) {
					V v = Key.this.valueOf(configuration);
					return v != null ? v : value;
				}
			};
		}
	}

	String KEY_DELIMITER = ".";

	static Class<?> classForName(String className) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			return Throwing.unchecked(e);
		}
	}

	static Key<Boolean> keyOfBoolean(String key, Boolean defaultValue) {
		return Key.ofStringable(key, defaultValue, Boolean::valueOf);
	}

	static <X> Key<Class<? extends X>> keyOfClass(String key, Class<X> baseClass, Class<?> defaultValue) {
		return Key.ofStringable(key, defaultValue, Class::forName).map(c -> c.asSubclass(baseClass), c -> c);
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
		return getString(key).map(Configuration::classForName).map(c -> c.<X> asSubclass(baseType));
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

	default Configuration prefixed(Named value) {
		return prefixed(value.name());
	}

	default Configuration prefixed(String keyPrefix) {
		Require.nonEmpty(keyPrefix);
		return new Configuration() {

			@Override
			public Optional<String> getString(String key) {
				return Configuration.this.getString(keyPrefix + KEY_DELIMITER + key);
			}

			@Override
			public Stream<Tpl<String, String>> stream() {
				return Configuration.this.stream().map(tpl -> tpl.mapLeft(keyPrefix::concat));
			}

			@Override
			public Configuration with(String key, String value) {
				return Configuration.this.with(keyPrefix + KEY_DELIMITER + key, value);
			}
		};
	}

	default <V> Configuration with(Key<V> key, V value) {
		return key.toConfig(this::with, value);
	}

	Configuration with(String key, String value);
}
