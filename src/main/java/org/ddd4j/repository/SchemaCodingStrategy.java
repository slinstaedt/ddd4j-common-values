package org.ddd4j.repository;

import java.util.function.Consumer;

import org.ddd4j.collection.Cache;
import org.ddd4j.collection.Cache.ReadThrough;
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
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.Revision;

public enum SchemaCodingStrategy {

	PER_MESSAGE {

		@Override
		public <T> Decoder<T> decoder(Context context, Class<T> type) {
			SchemaFactory factory = context.get(SchemaFactory.KEY);
			return buf -> Promise.completed(factory.readSchema(buf).createReader(type).read(buf));
		}

		@Override
		public <T> Encoder<T> encoder(Context context, Class<T> type) {
			Schema<T> schema = context.get(SchemaFactory.KEY).createSchema(type);
			Schema.Writer<T> writer = schema.createWriter();
			return (buf, val) -> Promise.completed(buf.put(encode()).accept(schema::serialize).accept(b -> writer.write(b, val)));
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
			Reader<Fingerprint, SchemaEntry<?>> repository = context.get(Reader.Factory.KEY)
					.create(SCHEMA_REPOSITORY)
					.map(Fingerprint::asBuffer, b -> SchemaEntry.deserialize(context, b));
			ReadThrough<Fingerprint, Promise<SchemaEntry<?>>> cache = Cache
					.<Fingerprint, Promise<SchemaEntry<?>>> sharedOnEqualKey(
							a -> a.evict(Cache.EvictStrategy.LAST_ACQUIRED).withMaximumCapacity(context.conf(Cache.MAX_CAPACITY)))
					.withFactory(repository::getValue);
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
			return (buf, val) -> promise.thenReturnValue(buf.put(encode()).accept(fingerprint).accept(b -> writer.write(b, val)));
		}
	};

	public interface Decoder<T> {

		Promise<T> decode(ReadBuffer buffer);
	}

	public interface Encoder<T> {

		Promise<WriteBuffer> encode(WriteBuffer buffer, T value);
	}

	static final Key<Cache<Fingerprint, Revision>> SCHEMA_REVISIONS = null;
	static final ResourceDescriptor SCHEMA_REPOSITORY = ResourceDescriptor.of("schemata");

	public static SchemaCodingStrategy decode(byte b) {
		return SchemaCodingStrategy.values()[b];
	}

	public abstract <T> Decoder<T> decoder(Context context, Class<T> readerType);

	protected byte encode() {
		return (byte) ordinal();
	}

	public WriteBuffer encode(WriteBuffer buffer) {
		return buffer.put(encode());
	}

	public abstract <T> Encoder<T> encoder(Context context, Class<T> writerType);
}
