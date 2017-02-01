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
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;
import org.ddd4j.value.versioned.Uncommitted;

public class InbandSchemaHandler implements SchemaHandler {

	private enum MessageType {
		SCHEMA, COMMIT;
	}

	private final Cache.Pool<Bytes> bytesPool;
	private final Cache.Aside<Schema<?>, Promise<Revision>> schemaCache;
	private Revisions revisions;

	public InbandSchemaHandler(Cache.Pool<Bytes> bytesPool) {
		this.bytesPool = Require.nonNull(bytesPool);
		this.schemaCache = Cache.sharedOnEqualKey();
	}

	@Override
	public <K, V> Committer<K, V> committer(EventChannel<K, V> channel, SinkCommitter committer) {
		return attempt -> schemaCache.acquire(channel.eventSchema(), s -> schemaRevision(s, committer)).thenCompose(schemaRevision -> {
			try (WriteBuffer buffer = PooledBytes.createBuffer(bytesPool)) {
				channel.eventSchema().createWriter(buffer).writeAndFlush(attempt.getValue());
				return committer.tryCommit(Recorded.uncommitted(buffer.flip(), revisions)).handleSuccess(attempt::resulting);
			} catch (Exception e) {
				return Promise.failed(e);
			}
		});
	}

	@Override
	public <K, V> Publisher<K, V> publisher(EventChannel<K, V> channel, SourcePublisher publisher) {
		// TODO Auto-generated method stub
		return null;
	}

	private Promise<Revision> schemaRevision(Schema<?> schema, SinkCommitter committer) {
		try (WriteBuffer buffer = PooledBytes.createBuffer(bytesPool)) {
			schema.serializeFingerprintAndSchema(buffer);
			Uncommitted<ReadBuffer, ReadBuffer> attempt = Recorded.uncommitted(buffer.flip(), revisions);
			return committer.tryCommit(attempt).handleSuccess(CommitResult::getActual);
		}
	}
}
