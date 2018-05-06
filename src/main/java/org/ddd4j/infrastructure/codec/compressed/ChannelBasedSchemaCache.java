package org.ddd4j.infrastructure.codec.compressed;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.spi.ColdReader;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelRevision;
import org.ddd4j.schema.Fingerprint;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Context.IndexedService;
import org.ddd4j.util.collection.Cache;
import org.ddd4j.value.versioned.Revision;

class ChannelBasedSchemaCache implements SchemaCache {

	private final IndexedService<SchemaFactory> factory;
	private final ConcurrentMap<Fingerprint, Revision> locations;
	private final Cache.Aside<ChannelRevision, Promise<Schema<?>>> cache;
	private final ColdReader reader;

	ChannelBasedSchemaCache(Context context) {
		this.factory = context.specific(SchemaFactory.REF, SchemaFactory.KEY);
		this.locations = new ConcurrentHashMap<>();
		this.cache = Cache.sharedOnEqualKey(
				a -> a.evict(Cache.EvictStrategy.LAST_ACQUIRED).withMaximumCapacity(context.conf(Cache.MAX_CAPACITY)).on().evicted(
						p -> p.whenCompleteSuccessfully(s -> locations.remove(s.getFingerprint()))));
		this.reader = context.get(ColdReader.FACTORY).createColdReader();
	}

	@Override
	public Promise<Schema<?>> get(ChannelName name, Revision revision) {
		return cache.acquire(new ChannelRevision(name, revision),
				rev -> reader.getCommittedValue(rev).whenCompleteExceptionally(ex -> cache.evictAll(rev)).thenApply(
						buf -> factory.get(buf.getUnsignedVarInt()).readSchema(buf)));
	}

	@Override
	public void put(ChannelName name, Revision revision, Schema<?> schema) {
		cache.acquire(new ChannelRevision(name, revision), rev -> {
			locations.put(schema.getFingerprint(), revision);
			return Promise.completed(schema);
		});
	}

	@Override
	public Optional<Revision> revisionOf(Schema<?> schema) {
		return Optional.ofNullable(locations.get(schema.getFingerprint()));
	}
}