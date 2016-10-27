package org.ddd4j.value.collection;

@FunctionalInterface
public interface Index<K, V> extends Map<K, Seq<V>> {

	default Seq<V> lookup(K key) {
		return get(key).orElse(Seq.empty());
	}
}
