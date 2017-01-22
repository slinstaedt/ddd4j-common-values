package org.ddd4j.eventstore;

import org.ddd4j.collection.Cache;
import org.ddd4j.contract.Require;
import org.ddd4j.eventstore.EventStore.Committer;
import org.ddd4j.eventstore.EventStore.EventChannel;
import org.ddd4j.eventstore.EventStore.Publisher;
import org.ddd4j.eventstore.SchemaEventStore.SchemaHandler;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.io.Bytes;
import org.ddd4j.io.PooledBytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.Schema.Writer;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Uncommitted;

public class InbandSchemaHandler implements SchemaHandler {

	private final Cache.Pool<Bytes> bytesPool;
	private final Cache.Aside<Schema<?>, Promise<Revision>> schemaCache;

	public InbandSchemaHandler(Cache.Pool<Bytes> bytesPool) {
		this.bytesPool = Require.nonNull(bytesPool);
		this.schemaCache = Cache.sharedOnEqualKey();
	}

	@Override
	public <K, V> Committer<K, V> committer(EventChannel<K, V> channel, SinkCommitter committer) {
		Promise<Revision> revision = schemaCache.acquire(channel.eventSchema(), s -> saveSchema(s, committer));
		return u -> {
			try (WriteBuffer buffer = PooledBytes.createBuffer(bytesPool)) {
				Writer<V> writer = schema.createWriter(buffer);
				writer.writeAndFlush(value);
			}
		};
	}

	@Override
	public <K, V> Publisher<K, V> publisher(EventChannel<K, V> channel, SourcePublisher publisher) {
		// TODO Auto-generated method stub
		return null;
	}

	private Promise<Revision> saveSchema(Schema<?> schema, SinkCommitter committer) {
		try (WriteBuffer buffer = PooledBytes.createBuffer(bytesPool)) {
			schema.serializeFingerprintAndSchema(buffer);
			Uncommitted<ReadBuffer, ReadBuffer> attempt = Recorded.uncommitted(buffer.flip(), expected);
			return committer.tryCommit(attempt).handleSuccess(CommitResult::getActual);
		}
	}
}
