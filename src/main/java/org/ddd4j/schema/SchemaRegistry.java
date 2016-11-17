package org.ddd4j.schema;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.ddd4j.io.repository.Repository;
import org.ddd4j.io.repository.RepositoryFactory;
import org.ddd4j.schema.Schema.Fingerprint;

public interface SchemaRegistry {

	class MemoryBasedSchemaRegistry implements SchemaRegistry {

		private final Map<Fingerprint, Schema<?>> schemata;

		public MemoryBasedSchemaRegistry() {
			this.schemata = new ConcurrentHashMap<>();
		}

		@Override
		public Optional<Schema<?>> lookup(Fingerprint fingerprint) {
			return Optional.ofNullable(schemata.get(fingerprint));
		}

		@Override
		public void register(Schema<?> schema) {
			schemata.putIfAbsent(schema.getFingerprint(), schema);
		}
	}

	class RepositoryBasedSchemaRegistry implements SchemaRegistry {

		private final Repository<Fingerprint, Schema<?>> repository;

		public RepositoryBasedSchemaRegistry(RepositoryFactory factory) {
			this.repository = factory.create(null, null);
		}

		@Override
		public Optional<Schema<?>> lookup(Fingerprint fingerprint) {
			return repository.get(fingerprint);
		}

		@Override
		public void register(Schema<?> schema) {
			repository.put(schema.getFingerprint(), schema);
		}
	}

	Optional<Schema<?>> lookup(Fingerprint fingerprint);

	void register(Schema<?> schema);
}
