package org.ddd4j.infrastructure.codec.repository;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.codec.SchemaCodec;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.schema.Fingerprint;
import org.ddd4j.schema.Schema;
import org.ddd4j.spi.ServiceBinder;
import org.ddd4j.spi.ServiceConfigurer;
import org.ddd4j.util.Require;
import org.ddd4j.value.versioned.Revision;

public class RepositoryBasedSchemaCodec implements SchemaCodec {

	public static class Configurer implements ServiceConfigurer {

		@Override
		public void bindServices(ServiceBinder binder) {
			binder.bind(SchemaCodec.REF).toFactory(ctx -> new RepositoryBasedSchemaCodec(ctx.get(SchemaRepository.REF)));
		}
	}

	public static final String NAME = "repo";

	private final SchemaRepository repository;

	public RepositoryBasedSchemaCodec(SchemaRepository repository) {
		this.repository = Require.nonNull(repository);
	}

	@Override
	public Promise<Schema<?>> decode(ReadBuffer buffer, Revision revision, ChannelName name) {
		return repository.get(Fingerprint.deserialize(buffer));
	}

	@Override
	public Promise<?> encode(WriteBuffer buffer, Promise<Revision> revision, ChannelName name, Schema<?> schema) {
		schema.getFingerprint().serialize(buffer);
		return repository.put(schema);
	}

	@Override
	public String name() {
		return NAME;
	}
}
