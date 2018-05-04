package org.ddd4j.infrastructure.codec.repository;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.infrastructure.Pool;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.spi.Reader;
import org.ddd4j.infrastructure.channel.spi.Writer;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.schema.Fingerprint;
import org.ddd4j.schema.Schema;
import org.ddd4j.spi.Context;
import org.ddd4j.util.Throwing;
import org.ddd4j.util.collection.Cache;
import org.ddd4j.value.config.ConfKey;

class ChannelBasedSchemaRepository implements SchemaRepository {

	private static final ConfKey<ChannelName> REPO_NAME = ConfKey.of("schemaRepositoryName", ChannelName.of("schemata"), ChannelName::of);

	private static Schema<?> notEqual(Schema<?> oldSchema, Schema<?> newSchema) {
		if (Objects.equals(oldSchema, newSchema)) {
			return Throwing.unchecked(new Exception("not updated"));
		} else {
			return newSchema;
		}
	}

	private final Cache.WriteThrough<Fingerprint, Promise<Schema<?>>> cache;

	ChannelBasedSchemaRepository(Context context) {
		ChannelName repoName = context.conf(REPO_NAME);
		Function<Consumer<WriteBuffer>, ReadBuffer> bufferPool = context.get(Pool.BUFFERS).use(WriteBuffer::flip);
		Reader<Fingerprint, Schema<?>> reader = context.get(Reader.FACTORY).create(repoName).map(Fingerprint::asBuffer,
				b -> Schema.deserializeFromFactory(context, b));
		Writer<Fingerprint, Schema<?>> writer = context.get(Writer.FACTORY).createWriterClosingBuffers(repoName).map(Fingerprint::asBuffer,
				s -> bufferPool.apply(s::serializeWithFactoryName));
		Cache.Aside<Fingerprint, Promise<Schema<?>>> cache = Cache.<Fingerprint, Promise<Schema<?>>>sharedOnEqualKey(
				a -> a.evict(Cache.EvictStrategy.LAST_ACQUIRED).withMaximumCapacity(context.conf(Cache.MAX_CAPACITY)));
		this.cache = cache.writeThrough(
				fp -> reader.getValueFailOnMissing(fp).whenCompleteExceptionally(ex -> cache.evictAll(fp)).ordered(),
				(fp, n, o, u) -> (o.isPresent() ? o.get().thenCombine(n, ChannelBasedSchemaRepository::notEqual) : n).thenRun(u)
						.whenCompleteSuccessfully(e -> writer.put(fp, e)));
	}

	@Override
	public Promise<Schema<?>> get(Fingerprint fingerprint) {
		return cache.get(fingerprint);
	}

	@Override
	public Promise<Schema<?>> put(Schema<?> schema) {
		return cache.put(schema.getFingerprint(), Promise.completed(schema));
	}
}