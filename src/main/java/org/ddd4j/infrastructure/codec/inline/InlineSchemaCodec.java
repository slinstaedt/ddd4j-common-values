package org.ddd4j.infrastructure.codec.inline;

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

public class InlineSchemaCodec implements SchemaCodec {

	public static class Configurer implements ServiceConfigurer {

		@Override
		public void bindServices(ServiceBinder binder) {
			binder.bind(SchemaCodec.REF).toFactory(ctx -> new InlineSchemaCodec(ctx.specific(SchemaFactory.REF)));
		}
	}

	public static final String NAME = "inline";

	private final NamedService<SchemaFactory> factory;

	public InlineSchemaCodec(NamedService<SchemaFactory> factory) {
		this.factory = Require.nonNull(factory);
	}

	@Override
	public Promise<Schema<?>> decode(ReadBuffer buffer, Revision revision, ChannelName name) {
		return Promise.completed(Schema.deserializeFromFactory(factory, buffer));
	}

	@Override
	public Promise<?> encode(WriteBuffer buffer, Promise<Revision> revision, ChannelName name, Schema<?> schema) {
		schema.serializeWithFactoryName(buffer);
		return Promise.completed();
	}

	@Override
	public String name() {
		return NAME;
	}
}
