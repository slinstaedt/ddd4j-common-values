package org.ddd4j.infrastructure.codec.compressed;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.codec.SchemaCodec;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.spi.Context.NamedService;
import org.ddd4j.spi.ServiceBinder;
import org.ddd4j.spi.ServiceConfigurer;
import org.ddd4j.util.Require;
import org.ddd4j.value.versioned.Revision;

public class CompressedSchemaCodec implements SchemaCodec {

	public static class Configurer implements ServiceConfigurer {

		@Override
		public void bindServices(ServiceBinder binder) {
			binder.bind(SchemaCodec.REF)
					.toFactory(ctx -> new CompressedSchemaCodec(ctx.specific(SchemaFactory.REF), ctx.get(SchemaCache.REF)));
		}
	}

	public static final String NAME = "compressed";

	private final NamedService<SchemaFactory> factory;
	private final SchemaCache cache;

	public CompressedSchemaCodec(NamedService<SchemaFactory> factory, SchemaCache cache) {
		this.factory = Require.nonNull(factory);
		this.cache = Require.nonNull(cache);
	}

	@Override
	public Promise<Schema<?>> decode(ReadBuffer buffer, Revision revision, ChannelName name) {
		if (buffer.getBoolean()) {
			return cache.get(name, Revision.deserialize(buffer));
		} else {
			return Promise.completed(Schema.deserializeFromFactory(factory, buffer));
		}
	}

	@Override
	public Promise<?> encode(WriteBuffer buffer, Promise<Revision> revision, ChannelName name, Schema<?> schema) {
		cache.revisionOf(schema).ifPresentOrElse(rev -> {
			buffer.putBoolean(true).accept(rev::serialize);
		}, () -> {
			buffer.putBoolean(false).accept(schema::serializeWithFactoryName);
			revision.whenCompleteSuccessfully(rev -> cache.put(name, rev, schema));
		});
		return Promise.completed();
	}

	@Override
	public String name() {
		return NAME;
	}
}
