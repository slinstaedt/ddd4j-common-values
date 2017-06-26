package org.ddd4j.repository;

import java.util.function.Consumer;

import org.ddd4j.Require;
import org.ddd4j.collection.Cache;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.PooledBytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.repository.api.Reader;
import org.ddd4j.repository.api.Writer;
import org.ddd4j.schema.Fingerprint;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.SchemaEntry;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Context.NamedService;
import org.ddd4j.spi.Key;

public enum SchemaCodec {

	PER_MESSAGE {

		@Override
		public <T> Decoder<T> decoder(Context context, Class<T> type) {
			NamedService<SchemaFactory> factory = context.specific(SchemaFactory.KEY);
			return buf -> Promise.completed(factory.withOrFail(buf.getUTF()).readSchema(buf).createReader(type).read(buf));
		}

		@Override
		public <T> Encoder<T> encoder(Context context, Class<T> type) {
			SchemaFactory factory = context.get(SchemaFactory.KEY);
			Schema<T> schema = factory.createSchema(type);
			Schema.Writer<T> writer = schema.createWriter();
			return (buf, val) -> Promise
					.completed(buf.put(encodeType()).putUTF(factory.name()).accept(schema::serialize).accept(b -> writer.write(b, val)));
		}
	},
	IN_BAND {

		@Override
		public <T> Decoder<T> decoder(Context context, Class<T> type) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public <T> Encoder<T> encoder(Context context, Class<T> type) {
			// TODO Auto-generated method stub
			return null;
		}
	},
	OUT_OF_BAND {

		@Override
		public <T> Decoder<T> decoder(Context context, Class<T> type) {
			Cache.ReadThrough<Fingerprint, Promise<SchemaEntry<?>>> cache = context.get(ENTRY_CACHE);
			return buf -> cache.acquire(Fingerprint.deserialize(buf)).thenApply(e -> e.getSchema().createReader(type).read(buf));
		}

		@Override
		public <T> Encoder<T> encoder(Context context, Class<T> type) {
			SchemaEntry<T> entry = SchemaEntry.create(context.get(SchemaFactory.KEY), type);
			WriteBuffer buffer = context.get(PooledBytes.FACTORY).get().buffered();
			Promise<Void> promise = context.get(Writer.Factory.KEY)
					.create(SCHEMA_REPOSITORY)
					.put(entry.toRecorded(buffer))
					.thenRun(buffer::close);
			Consumer<WriteBuffer> fingerprint = entry.getSchema().getFingerprint()::serialize;
			Schema.Writer<T> writer = entry.getSchema().createWriter();
			return (buf, val) -> promise.thenReturnValue(buf.put(encodeType()).accept(fingerprint).accept(b -> writer.write(b, val)));
		}
	};

	public interface Decoder<T> {

		Promise<T> decode(ReadBuffer buffer);
	}

	public interface Encoder<T> {

		Promise<WriteBuffer> encode(WriteBuffer buffer, T value);
	}

	public static class Factory {

		private final Context context;

		Factory(Context context) {
			this.context = Require.nonNull(context);
		}

		public <T> Decoder<T> decoder(Class<T> readerType) {
			Require.nonNull(readerType);
			return buf -> decodeType(buf.get()).decoder(context, readerType).decode(buf);
		}

		public <T> Encoder<T> encoder(Class<T> writerType) {
			Require.nonNull(writerType);
			return context.configuration()
					.getEnum(SchemaCodec.class, "schemaEncodingStrategy")
					.orElse(SchemaCodec.OUT_OF_BAND)
					.encoder(context, writerType);
		}
	}

	public static final Key<Factory> KEY = Key.of(Factory.class, Factory::new);
	private static final ResourceDescriptor SCHEMA_REPOSITORY = ResourceDescriptor.of("schemata");
	private static final Key<Cache.ReadThrough<Fingerprint, Promise<SchemaEntry<?>>>> ENTRY_CACHE = Key.of("schemaEntryCache", ctx -> {
		Reader<Fingerprint, SchemaEntry<?>> repository = ctx.get(Reader.Factory.KEY).create(SCHEMA_REPOSITORY).map(Fingerprint::asBuffer,
				b -> SchemaEntry.deserialize(ctx, b));
		return Cache
				.<Fingerprint, Promise<SchemaEntry<?>>> sharedOnEqualKey(
						a -> a.evict(Cache.EvictStrategy.LAST_ACQUIRED).withMaximumCapacity(ctx.conf(Cache.MAX_CAPACITY)))
				.withFactory(fp -> repository.getValue(fp).ordered());
	});

	static SchemaCodec decodeType(byte b) {
		return SchemaCodec.values()[b];
	}

	public abstract <T> Decoder<T> decoder(Context context, Class<T> readerType);

	byte encodeType() {
		return (byte) ordinal();
	}

	public abstract <T> Encoder<T> encoder(Context context, Class<T> writerType);
}
