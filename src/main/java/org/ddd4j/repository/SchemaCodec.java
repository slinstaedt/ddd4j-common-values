package org.ddd4j.repository;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
import org.ddd4j.collection.Cache;
import org.ddd4j.collection.Cache.WriteThrough;
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
			SchemaRepository repository = context.get(SCHEMA_REPOSITORY);
			return (buf, rev) -> repository.get(Fingerprint.deserialize(buf)).thenApply(s -> s.read(type, buf));
		}

		@Override
		public <T> Encoder<T> encoder(Context context, Type<T> type, ChannelName name) {
			Schema<T> schema = context.get(SchemaFactory.KEY).createSchema(type);
			Promise<?> promise = context.get(SCHEMA_REPOSITORY).put(schema);
			Schema.Writer<T> writer = schema.createWriter();
			return (buf, rev, val) -> promise
					.thenReturnValue(buf.put(encodeType()).accept(schema.getFingerprint()::serialize).accept(b -> writer.write(b, val)));
		}
	},
	PER_MESSAGE {

		@Override
		public <T> Decoder<T> decoder(Context context, Type<T> type, ChannelName name) {
			SchemaRevisionCache cache = context.get(SCHEMA_REVISION_CACHE);
			return (buf, rev) -> {
				Schema<?> schema = Schema.deserializeFromFactory(context, buf);
				cache.put(name, rev, schema);
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
			SchemaRevisionCache cache = context.get(SCHEMA_REVISION_CACHE);
			return (buf, rev) -> cache.get(name, Revision.deserialize(buf)).thenApply(s -> s.read(type, buf));
		}

		@Override
		public <T> Encoder<T> encoder(Context context, Type<T> type, ChannelName name) {
			SchemaRevisionCache cache = context.get(SCHEMA_REVISION_CACHE);
			Schema<T> schema = context.get(SchemaFactory.KEY).createSchema(type);
			Schema.Writer<T> writer = schema.createWriter();
			return (buf, rev, val) -> {
				Optional<Revision> revision = cache.revisionOf(schema);
				if (revision.isPresent()) {
					return Promise.completed(buf.put(encodeType()).accept(revision.get()::serialize).accept(b -> writer.write(b, val)));
				} else {
					rev.whenCompleteSuccessfully(r -> cache.put(name, r, schema));
					return encodeWithSchema(buf, val, schema, writer);
				}
			};
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

	private static class SchemaRevisionCache {

		private final Context context;
		private final ConcurrentMap<Fingerprint, Revision> locations;
		private final Cache.Aside<ChannelRevision, Promise<Schema<?>>> cache;
		private final ColdReader reader;

		SchemaRevisionCache(Context context) {
			this.context = Require.nonNull(context);
			this.locations = new ConcurrentHashMap<>();
			this.cache = Cache.sharedOnEqualKey(
					a -> a.evict(Cache.EvictStrategy.LAST_ACQUIRED).withMaximumCapacity(context.conf(Cache.MAX_CAPACITY)).on().evicted(
							p -> p.whenCompleteSuccessfully(s -> locations.remove(s.getFingerprint()))));
			this.reader = context.get(ColdReader.FACTORY).createColdReader();
		}

		Promise<Schema<?>> get(ChannelName name, Revision revision) {
			return cache.acquire(new ChannelRevision(name, revision), rev -> reader.getCommittedValue(rev)
					.whenCompleteExceptionally(ex -> cache.evictAll(rev))
					.thenApply(buf -> Schema.deserializeFromFactory(context, buf)));
		}

		void put(ChannelName name, Revision revision, Schema<?> schema) {
			cache.acquire(new ChannelRevision(name, revision), rev -> {
				locations.put(schema.getFingerprint(), revision);
				return Promise.completed(schema);
			});
		}

		Optional<Revision> revisionOf(Schema<?> schema) {
			return Optional.ofNullable(locations.get(schema.getFingerprint()));
		}
	}

	private static class SchemaRepository {

		private static final ChannelName REPO_NAME = ChannelName.of("schemata");

		private static Schema<?> notEqual(Schema<?> oldSchema, Schema<?> newSchema) {
			if (Objects.equals(oldSchema, newSchema)) {
				return Throwing.unchecked(new Exception("not updated"));
			} else {
				return newSchema;
			}
		}

		private final WriteThrough<Fingerprint, Promise<Schema<?>>> cache;

		SchemaRepository(Context context) {
			WriteBuffer.Pool bufferPool = context.get(WriteBuffer.POOL);
			Reader<Fingerprint, Schema<?>> reader = context.get(Reader.FACTORY).create(REPO_NAME).map(Fingerprint::asBuffer,
					b -> Schema.deserializeFromFactory(context, b));
			Writer<Fingerprint, Schema<?>> writer = context.get(Writer.FACTORY).createClosingBuffers(REPO_NAME).map(Fingerprint::asBuffer,
					s -> bufferPool.serialized(s::serializeWithFactoryName));
			this.cache = Cache
					.<Fingerprint, Promise<Schema<?>>>sharedOnEqualKey(
							a -> a.evict(Cache.EvictStrategy.LAST_ACQUIRED).withMaximumCapacity(context.conf(Cache.MAX_CAPACITY)))
					.writeThrough(fp -> reader.getValueFailOnMissing(fp).ordered(),
							(fp, n, o, u) -> (o.isPresent() ? o.get().thenCombine(n, SchemaRepository::notEqual) : n).thenRun(u)
									.whenCompleteSuccessfully(e -> writer.put(fp, e)));
		}

		Promise<Schema<?>> get(Fingerprint fingerprint) {
			return cache.get(fingerprint);
		}

		Promise<Schema<?>> put(Schema<?> schema) {
			return cache.put(schema.getFingerprint(), Promise.completed(schema));
		}
	}

	public static final Key<Factory> FACTORY = Key.of(Factory.class, Factory::new);
	private static final ConfKey<SchemaCodec> SCHEMA_ENCODER = Configuration.keyOfEnum(SchemaCodec.class, "schemaEncoder",
			SchemaCodec.OUT_OF_BAND);
	private static final Key<SchemaRepository> SCHEMA_REPOSITORY = Key.of(SchemaRepository.class, SchemaRepository::new);
	private static final Key<SchemaRevisionCache> SCHEMA_REVISION_CACHE = Key.of(SchemaRevisionCache.class, SchemaRevisionCache::new);

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
