package org.ddd4j.repository;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
import org.ddd4j.collection.Cache;
import org.ddd4j.infrastructure.ChannelName;
import org.ddd4j.infrastructure.ChannelRevision;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.ColdReader;
import org.ddd4j.infrastructure.channel.Reader;
import org.ddd4j.infrastructure.channel.Writer;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.schema.Fingerprint;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.SchemaEntry;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Key;
import org.ddd4j.value.Type;
import org.ddd4j.value.config.ConfKey;
import org.ddd4j.value.config.Configuration;
import org.ddd4j.value.versioned.Revision;

public enum SchemaCodec {

	OUT_OF_BAND {

		@Override
		public <T> Decoder<T> decoder(Context context, Type<T> type, ChannelName name) {
			Cache.WriteThrough<Fingerprint, Promise<SchemaEntry<?>>> cache = context.get(SCHEMA_REPO);
			return (buf, rev) -> cache.get(Fingerprint.deserialize(buf)).thenApply(e -> e.getSchema().read(type, buf));
		}

		@Override
		public <T> Encoder<T> encoder(Context context, Type<T> type, ChannelName name) {
			SchemaEntry<T> entry = SchemaEntry.create(context.get(SchemaFactory.KEY), type);
			Fingerprint fp = entry.getFingerprint();
			Promise<?> promise = context.get(SCHEMA_REPO).put(fp, Promise.completed(entry));
			Schema.Writer<T> writer = entry.getSchema().createWriter();
			return (buf, rev, val) -> promise
					.thenReturnValue(buf.put(encodeType()).accept(fp::serialize).accept(b -> writer.write(b, val)));
		}
	},
	PER_MESSAGE {

		@Override
		public <T> Decoder<T> decoder(Context context, Type<T> type, ChannelName name) {
			SchemaLocationCache cache = context.get(SCHEMA_LOCATION_CACHE);
			return (buf, rev) -> {
				Schema<?> schema = Schema.deserializeFromFactory(context, buf);
				cache.put(schema, new ChannelRevision(name, rev));
				return Promise.completed(schema.read(type, buf));
			};
		}

		@Override
		public <T> Encoder<T> encoder(Context context, Type<T> type, ChannelName name) {
			Schema<T> schema = context.get(SchemaFactory.KEY).createSchema(type);
			Schema.Writer<T> writer = schema.createWriter();
			return (buf, rev, val) -> encodeWithSchema(buf, val, schema, writer);
		}
	},
	IN_BAND {

		@Override
		public <T> Decoder<T> decoder(Context context, Type<T> type, ChannelName name) {
			SchemaLocationCache cache = context.get(SCHEMA_LOCATION_CACHE);
			return (buf, rev) -> cache.get(rev(name, buf)).thenApply(s -> s.read(type, buf));
		}

		@Override
		public <T> Encoder<T> encoder(Context context, Type<T> type, ChannelName name) {
			SchemaLocationCache cache = context.get(SCHEMA_LOCATION_CACHE);
			Schema<T> schema = context.get(SchemaFactory.KEY).createSchema(type);
			Schema.Writer<T> writer = schema.createWriter();
			return (buf, rev, val) -> {
				Optional<ChannelRevision> revision = cache.locationOf(schema);
				if (revision.isPresent()) {
					return Promise.completed(buf.put(encodeType()).accept(revision.get()::serialize).accept(b -> writer.write(b, val)));
				} else {
					rev.whenCompleteSuccessfully(r -> cache.put(schema, new ChannelRevision(name, r)));
					return encodeWithSchema(buf, val, schema, writer);
				}
			};
		}

		private ChannelRevision rev(ChannelName name, ReadBuffer buffer) {
			return new ChannelRevision(name, Revision.deserialize(buffer));
		}
	};

	public interface Decoder<T> {

		Promise<T> decode(ReadBuffer buffer, Revision revision);
	}

	public interface Encoder<T> {

		Promise<WriteBuffer> encode(WriteBuffer buffer, Promise<Revision> revision, T value);
	}

	public static class Factory {

		private final Context context;

		Factory(Context context) {
			this.context = Require.nonNull(context);
		}

		public <T> Decoder<T> decoder(Type<T> readerType, ChannelName channelName) {
			Require.nonNullElements(readerType, channelName);
			return (buf, rev) -> decodeType(buf.get()).decoder(context, readerType, channelName).decode(buf, rev);
		}

		public <T> Encoder<T> encoder(Type<T> writerType, ChannelName channelName) {
			Require.nonNull(writerType);
			return context.conf(SCHEMA_ENCODER).encoder(context, writerType, channelName);
		}
	}

	private static class SchemaLocationCache {

		private final Context context;
		private final ConcurrentMap<Fingerprint, ChannelRevision> locations;
		private final Cache.Aside<ChannelRevision, Promise<Schema<?>>> cache;
		private final ColdReader reader;

		SchemaLocationCache(Context context) {
			this.context = Require.nonNull(context);
			this.locations = new ConcurrentHashMap<>();
			this.cache = Cache.sharedOnEqualKey(
					a -> a.evict(Cache.EvictStrategy.LAST_ACQUIRED).withMaximumCapacity(context.conf(Cache.MAX_CAPACITY)).on().evicted(
							p -> p.whenCompleteSuccessfully(s -> locations.remove(s.getFingerprint()))));
			this.reader = context.get(ColdReader.FACTORY).createColdReader();
		}

		Promise<Schema<?>> get(ChannelRevision revision) {
			return cache.acquire(revision, rev -> reader.getCommittedValue(rev)
					.whenCompleteExceptionally(ex -> cache.evictAll(rev))
					.thenApply(buf -> Schema.deserializeFromFactory(context, buf)));
		}

		Optional<ChannelRevision> locationOf(Schema<?> schema) {
			return Optional.ofNullable(locations.get(schema.getFingerprint()));
		}

		void put(Schema<?> schema, ChannelRevision revision) {
			cache.acquire(revision, rev -> {
				locations.put(schema.getFingerprint(), rev);
				return Promise.completed(schema);
			});
		}
	}

	public static final Key<Factory> FACTORY = Key.of(Factory.class, Factory::new);
	private static final ConfKey<SchemaCodec> SCHEMA_ENCODER = Configuration.keyOfEnum(SchemaCodec.class, "schemaEncoder",
			SchemaCodec.OUT_OF_BAND);

	private static final Key<Cache.WriteThrough<Fingerprint, Promise<SchemaEntry<?>>>> SCHEMA_REPO = Key.of("schemaRepository", ctx -> {
		ChannelName schemaRepositoryDescriptor = ChannelName.of("schemata");
		Supplier<WriteBuffer> bufferFactory = ctx.get(WriteBuffer.FACTORY);
		Reader<Fingerprint, SchemaEntry<?>> reader = ctx.get(Reader.FACTORY).create(schemaRepositoryDescriptor).map(Fingerprint::asBuffer,
				b -> SchemaEntry.deserialize(ctx, b));
		Writer<Fingerprint, SchemaEntry<?>> writer = ctx.get(Writer.FACTORY)
				.createClosingBuffers(schemaRepositoryDescriptor)
				.map(Fingerprint::asBuffer, e -> e.serialized(bufferFactory)); // XXX
		Throwing.TBiFunction<SchemaEntry<?>, SchemaEntry<?>, SchemaEntry<?>> notEqual = (o, n) -> !Objects.equals(o, n) ? n
				: Throwing.unchecked(new Exception("not updated"));
		return Cache
				.<Fingerprint, Promise<SchemaEntry<?>>>sharedOnEqualKey(
						a -> a.evict(Cache.EvictStrategy.LAST_ACQUIRED).withMaximumCapacity(ctx.conf(Cache.MAX_CAPACITY)))
				.writeThrough(fp -> reader.getValueFailOnMissing(fp).ordered(),
						(fp, n, o, u) -> (o.isPresent() ? o.get().thenCombine(n, notEqual) : n).thenRun(u)
								.whenCompleteSuccessfully(e -> writer.put(fp, e)));
	});

	private static final Key<SchemaLocationCache> SCHEMA_LOCATION_CACHE = Key.of(SchemaLocationCache.class, SchemaLocationCache::new);

	static SchemaCodec decodeType(byte b) {
		return SchemaCodec.values()[b];
	}

	public abstract <T> Decoder<T> decoder(Context context, Type<T> readerType, ChannelName name);

	byte encodeType() {
		return (byte) ordinal();
	}

	private static <T> Promise<WriteBuffer> encodeWithSchema(WriteBuffer buffer, T value, Schema<T> schema, Schema.Writer<T> writer) {
		return Promise.completed(
				buffer.put(PER_MESSAGE.encodeType()).accept(schema::serializeWithFactoryName).accept(b -> writer.write(b, value)));
	}

	public abstract <T> Encoder<T> encoder(Context context, Type<T> writerType, ChannelName name);
}
