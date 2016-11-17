package org.ddd4j.io.repository;

import java.util.Optional;

public interface Repository<K, V> {

	Optional<V> get(K key);

	void put(K key, V value);
}
