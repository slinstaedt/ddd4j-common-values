package org.ddd4j.schema;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

	Optional<Schema<?>> lookup(Fingerprint fingerprint);

	void register(Schema<?> schema);
}
