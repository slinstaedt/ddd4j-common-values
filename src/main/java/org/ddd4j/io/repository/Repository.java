package org.ddd4j.io.repository;

public interface Repository<K, V> {

	V get(K key);

	void put(K key, V value);
}
