package org.ddd4j.io;

import static java.lang.Integer.bitCount;
import static java.lang.Integer.highestOneBit;
import static java.lang.Integer.min;

import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.ddd4j.Require;
import org.ddd4j.collection.Cache;
import org.ddd4j.collection.Cache.Pool;
import org.ddd4j.spi.Key;
import org.ddd4j.value.collection.Configuration;

public class PooledBytes<B extends Bytes> extends Bytes {

	static final Configuration.Key<Integer> BUFFER_SIZE = Configuration.keyOfInteger("bufferSize", 4096);
	static final Configuration.Key<Integer> POOL_SIZE = Configuration.keyOfInteger("poolSize", 512);

	public static final Key<Cache.ReadThrough<Integer, byte[]>> BYTE_ARRAY_CACHE = Key.of("byteArrayCache",
			ctx -> Cache.<Integer, byte[]>exclusive(b -> b.length)
					.evict()
					.withMaximumCapacity(ctx.conf(POOL_SIZE))
					.lookupValues(Cache.KeyLookup.CEILING, k -> min(bitCount(k) == 1 ? k : highestOneBit(k) << 1, 4096))
					.withFactory(byte[]::new));

	public static final Key<Cache.ReadThrough<Integer, java.nio.ByteBuffer>> BYTE_BUFFER_CACHE = Key.of("byteBufferCache",
			ctx -> ctx.get(BYTE_ARRAY_CACHE).wrapEntries(java.nio.ByteBuffer::wrap, java.nio.ByteBuffer::array));

	public static final Key<Cache.ReadThrough<Integer, Bytes.Arrayed>> BYTES_CACHE = Key.of("bytesCache",
			ctx -> ctx.get(BYTE_ARRAY_CACHE).wrapEntries(Bytes.Arrayed::new, Bytes.Arrayed::backing));

	public static final Key<Supplier<PooledBytes<Bytes.Arrayed>>> FACTORY = Key.of("pooledBytesFactory", ctx -> {
		Pool<Bytes.Arrayed> pool = ctx.get(BYTES_CACHE).pooledBy(ctx.conf(BUFFER_SIZE));
		return () -> new PooledBytes<>(pool);
	});

	@SuppressWarnings("resource")
	public static <B extends Bytes> WriteBuffer createBuffer(Cache.Pool<B> pool) {
		return new PooledBytes<>(pool).buffered();
	}

	private final Cache.Pool<B> pool;
	private final NavigableMap<Integer, B> bytes;

	public PooledBytes(Cache.Pool<B> pool) {
		this.pool = Require.nonNull(pool);
		this.bytes = new TreeMap<>();
	}

	@Override
	public void close() {
		bytes.values().forEach(pool::release);
		bytes.clear();
	}

	@Override
	public byte get(int index) {
		return partition(index, false).get(index);
	}

	@Override
	public int length() {
		return Integer.MAX_VALUE;
	}

	private Bytes partition(int index, boolean create) {
		Entry<Integer, B> entry = null;
		if (create) {
			while ((entry = bytes.floorEntry(index)) == null) {
				Integer nextIndex = bytes.isEmpty() ? 0 : bytes.lastKey() + bytes.lastEntry().getValue().length();
				bytes.put(nextIndex, pool.acquire());
			}
			return entry.getValue();
		} else {
			entry = bytes.floorEntry(index);
			return entry != null ? entry.getValue() : Bytes.NULL;
		}
	}

	@Override
	public Bytes put(int index, byte b) {
		partition(index, true).put(index, b);
		return this;
	}
}