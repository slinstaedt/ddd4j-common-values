package org.ddd4j.value.collection;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

@FunctionalInterface
public interface Tuples<K, V> {

	static <K, V> Tuples<K, V> ofMap(Map<K, V> map) {
		return f -> f.apply((k) -> Tpl.of(k, map.get(k)), (k, v) -> Tpl.of(k, map.put(k, v)));
	}

	Tpl<K, V> apply(BiFunction<Function<K, Tpl<K, V>>, BiFunction<K, V, Tpl<K, V>>, Tpl<K, V>> f);

	default boolean contains(K key) {
		return get(key) != null;
	}

	default Tpl<K, V> get(K key) {
		return apply((g, s) -> g.apply(key));
	}

	default Tuples<V, K> inverse() {
		return c -> null;
	}

	default Tpl<K, V> put(K key, V value) {
		return apply((g, s) -> s.apply(key, value));
	}
}
