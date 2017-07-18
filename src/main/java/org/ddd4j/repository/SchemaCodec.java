package org.ddd4j.repository;

import java.util.Objects;
import java.util.function.Supplier;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
import org.ddd4j.collection.Cache;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.Reader;
import org.ddd4j.infrastructure.channel.Writer;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.schema.Fingerprint;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.SchemaEntry;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Context.NamedService;
import org.ddd4j.spi.Key;
import org.ddd4j.value.Type;
import org.ddd4j.value.collection.Configuration;
import org.ddd4j.value.versioned.Revision;

public enum SchemaCodec {

	OUT_OF_BAND {

		@Override
		public <T> Decoder<T> decoder(Context context, Type<T> type) {
			Cache.WriteThrough<Fingerprint, Promise<SchemaEntry<?>>> cache = context.get(SCHEMA_REPO);
			return buf -> cache.get(Fingerprint.deserialize(buf)).thenApply(e -> e.getSchema().createReader(type).read(buf));
		}

		@Override
		public <T> Encoder<T> encoder(Context context, Type<T> type) {
			SchemaEntry<T> entry = SchemaEntry.create(context.get(SchemaFactory.KEY), type);
			Fingerprint fp = entry.getFingerprint();
			Promise<?> promise = context.get(SCHEMA_REPO).put(fp, Promise.completed(entry));
			Schema.Writer<T> writer = entry.getSchema().createWriter();
			return (buf, val) -> promise.thenReturnValue(buf.put(encodeType()).accept(fp::serialize).accept(b -> writer.write(b, val)));
		}
	},
	PER_MESSAGE {

		@Override
		public <T> Decoder<T> decoder(Context context, Type<T> type) {
			NamedService<SchemaFactory> factory = context.specific(SchemaFactory.KEY);
			return buf -> Promise.completed(factory.withOrFail(buf.getUTF()).readSchema(buf).createReader(type).read(buf));
		}

		@Override
		public <T> Encoder<T> encoder(Context context, Type<T> type) {
			SchemaFactory factory = context.get(SchemaFactory.KEY);
			Schema<T> schema = factory.createSchema(type);
			Schema.Writer<T> writer = schema.createWriter();
			return (buf, val) -> Promise
					.completed(buf.put(encodeType()).putUTF(factory.name()).accept(schema::serialize).accept(b -> writer.write(b, val)));
		}
	},
	IN_BAND {

		@Override
		public <T> Decoder<T> decoder(Context context, Type<T> type) {
			Cache.WriteThrough<Revision, Promise<SchemaEntry<?>>> cache = context.get(SCHEMA_OFFSETS);
			return buf -> cache.get(new Revision(buf)).thenApply(e -> e.getSchema().createReader(type).read(buf));
		}

		@Override
		public <T> Encoder<T> encoder(Context context, Type<T> type) {
			Cache.WriteThrough<Revision, Promise<SchemaEntry<?>>> cache = context.get(SCHEMA_OFFSETS);
			SchemaEntry<T> entry = SchemaEntry.create(context.get(SchemaFactory.KEY), type);
			// TODO Auto-generated method stub
			Promise<?> promise = null;
			Schema.Writer<T> writer = entry.getSchema().createWriter();
			return (buf, val) -> promise.thenReturnValue(buf.put(encodeType()).accept(b -> writer.write(b, val)));
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

		public <T> Decoder<T> decoder(Type<T> readerType) {
			Require.nonNull(readerType);
			return buf -> decodeType(buf.get()).decoder(context, readerType).decode(buf);
		}

		public <T> Encoder<T> encoder(Type<T> writerType) {
			Require.nonNull(writerType);
			return context.conf(SCHEMA_ENCODER).encoder(context, writerType);
		}
	}

	public static final Key<Factory> FACTORY = Key.of(Factory.class, Factory::new);
	private static final Configuration.Key<SchemaCodec> SCHEMA_ENCODER = Configuration.keyOfEnum(SchemaCodec.class, "schemaEncoder",
			SchemaCodec.OUT_OF_BAND);
	private static final Key<Cache.WriteThrough<Fingerprint, Promise<SchemaEntry<?>>>> SCHEMA_REPO = Key.of("schemaRepository", ctx -> {
		ResourceDescriptor schemaRepositoryDescriptor = ResourceDescriptor.of("schemata");
		Supplier<WriteBuffer> bufferFactory = ctx.get(WriteBuffer.FACTORY);
		Reader<Fingerprint, SchemaEntry<?>> reader = ctx.get(Reader.FACTORY).create(schemaRepositoryDescriptor).map(Fingerprint::asBuffer,
				b -> SchemaEntry.deserialize(ctx, b));
		Writer<Fingerprint, SchemaEntry<?>> writer = ctx.get(Writer.FACTORY)
				.createClosingBuffers(schemaRepositoryDescriptor)
				.map(Fingerprint::asBuffer, e -> e.serialized(bufferFactory));
		Throwing.TBiFunction<SchemaEntry<?>, SchemaEntry<?>, SchemaEntry<?>> notEqual = (o, n) -> !Objects.equals(o, n) ? n
				: Throwing.unchecked(new Exception("not updated"));
		return Cache
				.<Fingerprint, Promise<SchemaEntry<?>>>sharedOnEqualKey(
						a -> a.evict(Cache.EvictStrategy.LAST_ACQUIRED).withMaximumCapacity(ctx.conf(Cache.MAX_CAPACITY)))
				.writeThrough(fp -> reader.getValueFailOnMissing(fp).ordered(),
						(fp, n, o, u) -> (o.isPresent() ? o.get().thenCombine(n, notEqual) : n).thenRun(u)
								.whenCompleteSuccessfully(e -> writer.put(fp, e)));
	});
	private static final Key<Cache.WriteThrough<Revision, Promise<SchemaEntry<?>>>> SCHEMA_OFFSETS = Key.of("schemaOffsetsCache", ctx -> {
		throw new UnsupportedOperationException();
	});

	static SchemaCodec decodeType(byte b) {
		return SchemaCodec.values()[b];
	}

	public abstract <T> Decoder<T> decoder(Context context, Type<T> readerType);

	byte encodeType() {
		return (byte) ordinal();
	}

	public abstract <T> Encoder<T> encoder(Context context, Type<T> writerType);
}
