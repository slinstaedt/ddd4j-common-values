package org.ddd4j.infrastructure.codec.repository;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.schema.Fingerprint;
import org.ddd4j.schema.Schema;
import org.ddd4j.spi.Ref;

public interface SchemaRepository {

	Ref<SchemaRepository> REF = Ref.of(SchemaRepository.class, ChannelBasedSchemaRepository::new);

	Promise<Schema<?>> get(Fingerprint fingerprint);

	Promise<Schema<?>> put(Schema<?> schema);
}