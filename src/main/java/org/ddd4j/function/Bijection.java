package org.ddd4j.function;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reverse mapping for java values like enums. The value can implement {@link Mapped}, which returns the typed mapped key.
 * Afterwards you can instantiate a {@link Bijection} for the given value type, enabling the user to lookup the value by it's
 * typed key. The lookup ensures on construction, that every value has a unique key (is bijective).
 * <p>
 * This class is null-safe, meaning it will not accept null parameters nor ever return null values.
 *
 * @author sven.linstaedt
 *
 * @param <K> The value's mapped key type
 * @param <V> The value's type
 */
public class Bijection<K, V> implements Serializable {

    public static class Builder<K, V> {

        private final Class<? super K> keyType;
        private final Class<? super V> valueType;
        private final Mapper<K, V> mapper;
        private final boolean throwOnKeyCollision;

        public Builder(Class<? super K> keyType, Class<? super V> valueType, Mapper<K, V> mapper, boolean throwOnKeyCollision) {
            this.keyType = requireNonNull(keyType);
            this.valueType = requireNonNull(valueType);
            this.mapper = requireNonNull(mapper);
            this.throwOnKeyCollision = throwOnKeyCollision;
        }

        public Bijection<K, V> build(Iterable<V> values) {
            return build(values, null);
        }

        @SuppressWarnings("unchecked")
        public Bijection<K, V> build(Iterable<V> values, V fallbackValue) {
            List<Entry<K, V>> entries = new ArrayList<>();
            for (V value : values) {
                Set<Key<K>> keySet = new HashSet<>();
                for (K key : mapper.keysOf(value)) {
                    if (keyType.isArray()) {
                        keySet.add(Key.ofMultiple((K[]) key));
                    } else {
                        keySet.add(Key.ofSingle(key));
                    }
                }
                entries.add(new Entry<>(value, keySet));
            }
            return new Bijection<>(keyType, valueType, fallbackValue, entries, throwOnKeyCollision);
        }
    }

    public static class Entry<K, V> implements Serializable {

        private static final long serialVersionUID = 1L;

        public static <K, V> Entry<K, V> none(Key<K> key) {
            return new Entry<>(key);
        }

        private final V value;
        private final Iterable<Key<K>> keys;

        private Entry(Key<K> key) {
            this.value = null;
            this.keys = Collections.singleton(key);
        }

        public Entry(V value, Collection<Key<K>> keys) {
            this.value = requireNonNull(value);
            this.keys = Collections.unmodifiableCollection(new ArrayList<>(keys));
        }

        @SafeVarargs
        public Entry(V value, Key<K>... keys) {
            this(value, Arrays.asList(keys));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Entry<?, ?> other = (Entry<?, ?>) obj;
            if (value == null) {
                if (other.value != null) {
                    return false;
                }
            } else if (!value.equals(other.value)) {
                return false;
            }
            return true;
        }

        public Key<K> getKey() {
            Iterator<Key<K>> iterator = keys.iterator();
            if (iterator.hasNext()) {
                return iterator.next();
            } else {
                throw new IllegalStateException("No keys available");
            }
        }

        public Iterable<Key<K>> getKeys() {
            return keys;
        }

        public V getValue() {
            if (value != null) {
                return value;
            } else {
                throw new IllegalArgumentException("Key '" + keys + "' is not mapped to any entry");
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((value == null) ? 0 : value.hashCode());
            return result;
        }

        public boolean hasValue() {
            return value != null;
        }

        public boolean isEmpty() {
            return value == null;
        }

        @Override
        public String toString() {
            return "Entry [" + value + "<=" + keys + "]";
        }
    }

    public static class Key<K> implements Serializable {

        private static <K> Key<K> ofMultiple(K[] key) {
            return new Key<>(Arrays.asList(key));
        }

        public static <K> Key<K> ofSingle(K key) {
            return new Key<>(Collections.singletonList(key));
        }

        @SafeVarargs
        @SuppressWarnings("unchecked")
        public static <K> Key<K[]> ofComposite(K... key) {
            return (Key<K[]>) ofMultiple(key);
        }

        private static final long serialVersionUID = 1L;

        private final List<K> parts;

        public Key(List<K> key) {
            this.parts = new ArrayList<>(key);
        }

        public K component(int index) {
            return parts.get(index);
        }

        public boolean isNull() {
            for (K part : parts) {
                if (part != null) {
                    return false;
                }
            }
            return true;
        }

        @SafeVarargs
        public final boolean equal(K... key) {
            return parts.equals(Arrays.asList(key));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Key<?> other = (Key<?>) obj;
            if (parts == null) {
                if (other.parts != null) {
                    return false;
                }
            } else if (!parts.equals(other.parts)) {
                return false;
            }
            return true;
        }

        public int getCardinality() {
            return parts.size();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((parts == null) ? 0 : parts.hashCode());
            return result;
        }

        @Override
        public String toString() {
            return parts.toString();
        }
    }

    public interface KeyTypeMapper<K, V> extends Mapper<K, V> {

        Class<K> keyType(Class<? extends V> mappedType);

        boolean isFallbackValue(V mappedValue);
    }

    /**
     * Interface for being implemented by the value's class.
     *
     * @param <K> The value's mapped key type
     */
    public interface Mapped<K> {

        Iterable<K> keys();

        boolean isFallback();
    }

    public interface Mapper<K, V> {

        Iterable<K> keysOf(V mappedValue);
    }

    private static class SelfMappedMapper<K, V extends Bijection.Mapped<K>> implements KeyTypeMapper<K, V> {

        @Override
        public Iterable<K> keysOf(V mappedValue) {
            return mappedValue.keys();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<K> keyType(Class<? extends V> mappedType) {
            for (Type genericInterface : mappedType.getGenericInterfaces()) {
                if (genericInterface instanceof ParameterizedType) {
                    ParameterizedType type = (ParameterizedType) genericInterface;
                    if (type.getRawType() == Bijection.Mapped.class) {
                        Type keyType = type.getActualTypeArguments()[0];
                        if (keyType instanceof Class) {
                            return (Class<K>) keyType;
                        } else if (keyType instanceof ParameterizedType) {
                            ParameterizedType parameterizedKeyType = (ParameterizedType) keyType;
                            return (Class<K>) parameterizedKeyType.getRawType();
                        }
                    }
                }
            }

            throw new IllegalArgumentException("Unable to determine key type of mapped: " + mappedType);
        }

        @Override
        public boolean isFallbackValue(V mappedValue) {
            return mappedValue.isFallback();
        }

        public V fallbackValue(Iterable<V> values) {
            V fallbackValue = null;
            for (V value : values) {
                if (value.isFallback()) {
                    if (fallbackValue == null) {
                        fallbackValue = value;
                    } else {
                        throw new IllegalStateException(values + " defines duplicate fallback for: " + fallbackValue + " and "
                                + value);
                    }
                }
            }
            return fallbackValue;
        }
    }

    public static <K, V> Bijection.Builder<K, V> builder(
            Class<? super K> keyType,
            Class<? super V> valueType,
            Mapper<K, V> mapper,
            boolean throwOnKeyCollision) {
        return new Builder<>(keyType, valueType, mapper, throwOnKeyCollision);
    }

    public static <K, E extends Enum<E> & Bijection.Mapped<K>> Bijection<K, E> ofEnum(Class<E> enumType) {
        return ofEnum(enumType, true);
    }

    public static <K, E extends Enum<E> & Bijection.Mapped<K>> Bijection<K, E> ofEnum(
            Class<E> enumType,
            boolean throwOnKeyCollision) {
        SelfMappedMapper<K, E> mapper = new SelfMappedMapper<>();
        E fallbackValue = mapper.fallbackValue(Arrays.asList(enumType.getEnumConstants()));
        return ofEnum(enumType, fallbackValue, mapper, throwOnKeyCollision);
    }

    public static <K, E extends Enum<E>> Bijection<K, E> ofEnum(
            Class<E> enumType,
            Class<K> keyType,
            E fallbackValue,
            Mapper<K, E> mapper,
            boolean throwOnKeyCollision) {
        List<E> enumValues = Arrays.asList(enumType.getEnumConstants());
        return builder(keyType, enumType, mapper, throwOnKeyCollision).build(enumValues, fallbackValue);
    }

    public static <K, E extends Enum<E>> Bijection<K, E> ofEnum(
            Class<E> enumType,
            E fallbackValue,
            KeyTypeMapper<K, E> mapper,
            boolean throwOnKeyCollision) {
        return ofEnum(enumType, mapper.keyType(enumType), fallbackValue, mapper, throwOnKeyCollision);
    }

    public static <K, M extends Bijection.Mapped<K>> Bijection<K, M> ofMapped(Iterable<M> mappedValues) {
        return ofMapped(mappedValues, true);
    }

    public static <K, M extends Bijection.Mapped<K>> Bijection<K, M> ofMapped(
            Iterable<M> mappedValues,
            boolean throwOnKeyCollision) {
        @SuppressWarnings("unchecked")
        Class<M> mappedType = (Class<M>) mappedValues.iterator().next().getClass();
        SelfMappedMapper<K, M> mapper = new SelfMappedMapper<>();
        M fallbackValue = mapper.fallbackValue(mappedValues);
        return builder(mapper.keyType(mappedType), mappedType, mapper, throwOnKeyCollision).build(mappedValues, fallbackValue);
    }

    @SafeVarargs
    public static <K, M extends Bijection.Mapped<K>> Bijection<K, M> ofMapped(M... mappedValues) {
        return ofMapped(Arrays.asList(mappedValues), true);
    }

    private static final long serialVersionUID = 1L;

    private final Class<? super K> keyType;
    private final Class<? super V> valueType;
    private final V fallbackValue;
    private final Map<Key<K>, Entry<K, V>> keyMap;
    private final Map<V, Entry<K, V>> valueMap;
    private final int keyCardinality;

    public Bijection(
            Class<? super K> keyType,
            Class<? super V> valueType,
            V fallbackValue,
            Iterable<Entry<K, V>> entries,
            boolean throwOnKeyCollision) {
        this.keyType = requireNonNull(keyType);
        this.valueType = requireNonNull(valueType);
        this.fallbackValue = fallbackValue;

        int keyCardinality = -1;
        Map<Key<K>, Entry<K, V>> keyMap = new HashMap<>();
        for (Entry<K, V> entry : entries) {
            for (Key<K> key : entry.getKeys()) {
                if (keyMap.containsKey(key)) {
                    if (throwOnKeyCollision) {
                        throw new IllegalArgumentException("Key '" + key + "' of " + valueType + ". Entry" + entry
                                + " is already mapped to '" + keyMap.get(key) + "'");
                    }
                } else {
                    keyMap.put(key, entry);
                    if (keyCardinality != key.getCardinality()) {
                        if (keyCardinality <= 0) {
                            keyCardinality = key.getCardinality();
                        } else {
                            throw new IllegalArgumentException("Unmatched key cardinality of " + valueType + ": "
                                    + keyCardinality + ". Entry " + entry + " differs in " + key);
                        }
                    }
                }
            }
        }
        this.keyMap = Collections.unmodifiableMap(keyMap);

        Map<V, Entry<K, V>> valueMap = new HashMap<>();
        for (Entry<K, V> entry : entries) {
            valueMap.put(entry.getValue(), entry);
        }
        this.valueMap = Collections.unmodifiableMap(valueMap);

        this.keyCardinality = keyCardinality;
    }

    /**
     * Tests, if the given key is lookupable.
     *
     * @param key The key to test
     * @return true, if the key can be looked up, false otherwise
     */
    public boolean containsKey(Key<K> key) {
        return keyMap.containsKey(key);
    }

    /**
     * Tests, if the given value is mapped.
     *
     * @param value The value to test
     * @return true, if the value can be looked up, false otherwise
     */
    public boolean containsValue(V value) {
        return valueMap.containsKey(value);
    }

    /**
     * Retrieves the entry for the given key. This method will throw an {@link IllegalArgumentException}, if the key can not be
     * looked up. Use {@link #containsKey(K)} to check in advance.
     *
     * @param key The key to look up
     * @return the entry
     * @throws IllegalArgumentException If the key can not be looked up
     */
    public Entry<K, V> entryOfKey(Key<K> key) {
        Entry<K, V> entry = keyMap.get(key);
        if (entry != null) {
            return entry;
        } else if (fallbackValue != null && keyCardinality == key.getCardinality()) {
            return new Entry<>(fallbackValue, key);
        } else {
            return Entry.none(key);
        }
    }

    /**
     * Retrieves the keys for the given mapped value.
     *
     * @param value The value to retrieve the keys for
     * @return The keys for a value
     */
    public Entry<K, V> entryOfValue(V value) {
        Entry<K, V> entry = valueMap.get(value);
        if (entry != null) {
            return entry;
        } else {
            throw new IllegalArgumentException("Value '" + value + "' is not mapped to any entry of "
                    + getValueType().getName());
        }
    }

    public int getKeyCardinality() {
        return keyCardinality;
    }

    /**
     * @return The key type
     */
    public Class<? super K> getKeyType() {
        return keyType;
    }

    /**
     * @return The value type
     */
    public Class<? super V> getValueType() {
        return valueType;
    }

    public boolean isComposite() {
        return keyCardinality > 1;
    }

    /**
     * Retrieves the reverse enum value for the given key. This method will throw an {@link IllegalArgumentException}, if the
     * key can not be looked up. Use {@link #contains(Object)} to check in advance.
     *
     * @param key The key to look up
     * @return the mapped value
     * @throws IllegalArgumentException If the key can not be looked up
     */
    public V valueOf(K key) {
        return entryOfKey(Key.ofSingle(key)).getValue();
    }
}