package org.ddd4j.infrastructure.codec.compressed;

import java.util.Optional;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.schema.Schema;
import org.ddd4j.spi.Ref;
import org.ddd4j.value.versioned.Revision;

public interface SchemaCache {

	Ref<SchemaCache> REF = Ref.of(SchemaCache.class, ChannelBasedSchemaCache::new);

	Promise<Schema<?>> get(ChannelName name, Revision revision);

	void put(ChannelName name, Revision revision, Schema<?> schema);

	Optional<Revision> revisionOf(Schema<?> schema);
}