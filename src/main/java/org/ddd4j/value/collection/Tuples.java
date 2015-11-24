package org.ddd4j.value.collection;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

@FunctionalInterface
public interface Tuples<K, V> {

	static <K, V> Tuples<K, V> ofCollection(Collection<Tpl<K, V>> col) {
		return f -> f.apply((k) -> col.stream().filter(t -> t.testLeft(k::equals)).findFirst().get(),
				(k, v) -> Tpl.of(k, map.put(k, v)));
	}

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

	default Tuples<K, V> index() {
		return ofMap(toMap());
	}

	default Tuples<V, K> inverse() {
		// TODO
		return c -> null;
	}

	default Tpl<K, V> put(K key, V value) {
		return apply((g, s) -> s.apply(key, value));
	}

	default Map<K, V> toMap() {
		Map<K, V> map = new HashMap<>();
		for (Tpl<K, V> tpl : tuples()) {
			tpl.fold((k, v) -> map.put(k, v));
		}
		return map;
	}

	default Collection<Tpl<K, V>> tuples() {

	}
}
