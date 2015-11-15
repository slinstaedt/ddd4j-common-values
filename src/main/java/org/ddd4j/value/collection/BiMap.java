package org.ddd4j.value.collection;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

@FunctionalInterface
public interface BiMap<K, V> extends Map<K, V> {

	static <K, V> BiMap<K, V> create() {

	}

	Tpl<Map<K, V>, Map<V, K>> backing();

	@Override
	default void clear() {
		Tpl.consume(backing(), Map::clear);
	}

	@Override
	default boolean containsKey(Object key) {
		return backing().foldLeft(m -> m.containsKey(key));
	}

	@Override
	default boolean containsValue(Object value) {
		return backing().foldRight(m -> m.containsKey(value));
	}

	@Override
	default Set<Entry<K, V>> entrySet() {
		return Collections.unmodifiableSet(backing().foldLeft(Map::entrySet));
	}

	default V forcePut(K key, V value) {
		Ref<V> ref = Ref.create();
		put(key, value, UnaryOperator.identity(), ref::set);
		return ref.get();
	}

	@Override
	default V get(Object key) {
		return backing().foldLeft(m -> m.get(key));
	}

	default BiMap<V, K> inverse() {
		return backing()::reverse;
	}

	@Override
	default boolean isEmpty() {
		return backing().foldLeft(Map::isEmpty);
	}

	@Override
	default Set<K> keySet() {
		return Collections.unmodifiableSet(backing().foldLeft(Map::keySet));
	}

	@Override
	default V put(K key, V value) throws IllegalArgumentException {
		UnaryOperator<K> fail = k -> {
			throw new IllegalArgumentException("Value: " + value + " for key: " + key + " is already present");
		};
		Ref<V> ref = Ref.create();
		put(key, value, fail, ref::set);
		return ref.get();
	}

	default void put(K key, V value, UnaryOperator<K> existingKey, Consumer<V> existingValue) {
		backing().consume((kMap, vMap) -> {
			vMap.compute(value, (v, k) -> {
				K existing = k != null ? existingKey.apply(k) : key;
				existingValue.accept(kMap.put(key, value));
				return existing;
			});
		});
	}

	@Override
	default void putAll(Map<? extends K, ? extends V> map) {
		for (Entry<? extends K, ? extends V> entry : map.entrySet()) {
			put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	default V remove(Object key) {
		return backing().fold((kMap, vMap) -> {
			V value = kMap.remove(key);
			vMap.remove(value);
			return value;
		});
	}

	@Override
	default int size() {
		return backing().foldLeft(Map::size);
	}

	@Override
	default Set<V> values() {
		return Collections.unmodifiableSet(backing().foldRight(Map::keySet));
	}
}
