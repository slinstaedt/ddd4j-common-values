package org.ddd4j.infrastructure;

import java.nio.ByteBuffer;

import org.ddd4j.collection.Cache;
import org.ddd4j.collection.Cache.KeyLookupStrategy;
import org.ddd4j.infrastructure.registry.RegistryKey;;

public interface SingletonKeys {

	RegistryKey<Cache<Integer, ByteBuffer>> DIRECT_BYTE_BUFFER_POOL = (c, s) -> Cache.exclusive(ByteBuffer::capacity) //
			.on().released(ByteBuffer::clear) //
			.evict().withMaximumCapacity(c.getInteger("pool.bytebuffer.size").orElse(100)) //
			.lookupValues(KeyLookupStrategy.CEILING) //
			.withFactory(ByteBuffer::allocateDirect);
} 
