package org.ddd4j.infrastructure.codec;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.schema.Schema;
import org.ddd4j.spi.Ref;
import org.ddd4j.util.value.Named;
import org.ddd4j.value.versioned.Revision;

public interface SchemaCodec extends Named {

	Ref<SchemaCodec> REF = Ref.of(SchemaCodec.class);

	Promise<Schema<?>> decode(ReadBuffer buffer, Revision revision, ChannelName name);

	Promise<?> encode(WriteBuffer buffer, Promise<Revision> revision, ChannelName name, Schema<?> schema);
}
