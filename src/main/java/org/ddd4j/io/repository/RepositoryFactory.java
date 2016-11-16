package org.ddd4j.io.repository;

import org.ddd4j.schema.Schema;

public interface RepositoryFactory {

	<K, V> Repository<K, V> create(Schema<K> keySchema, Schema<V> valueSchema);
}
