package org.ddd4j.infrastructure.repository;

import java.util.Optional;

public interface Repository<K, V> {

	Optional<V> get(K key);

	void put(K key, V value);
}
