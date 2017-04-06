package org.ddd4j.infrastructure;

import static java.lang.Integer.bitCount;
import static java.lang.Integer.highestOneBit;
import static java.lang.Integer.min;

import java.nio.ByteBuffer;

import org.ddd4j.collection.Cache;
import org.ddd4j.collection.Cache.KeyLookup;
import org.ddd4j.spi.Key;

public interface SingletonKeys {

	Key<Cache.ReadThrough<Integer, byte[]>> BYTE_ARRAY_POOL = Key.of("byteArrayPool",
			ctx -> Cache.<Integer, byte[]> exclusive(b -> b.length)
					.evict()
					.withMaximumCapacity(ctx.configuration().getInteger("pool.bytearray.size").orElse(100))
					.lookupValues(KeyLookup.CEILING, k -> min(bitCount(k) > 1 ? highestOneBit(k) << 1 : k, 4096))
					.withFactory(byte[]::new));

	Key<Cache.ReadThrough<Integer, ByteBuffer>> DIRECT_BYTE_BUFFER_POOL = Key.of("byteBufferPool",
			ctx -> Cache.exclusive(ByteBuffer::capacity)
					.on()
					.released(ByteBuffer::clear)
					.evict()
					.withMaximumCapacity(ctx.configuration().getInteger("pool.bytebuffer.size").orElse(100))
					.lookupValues(KeyLookup.CEILING)
					.withFactory(ByteBuffer::allocateDirect));
}
