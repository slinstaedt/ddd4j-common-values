package org.ddd4j.infrastructure;

import static java.lang.Integer.bitCount;
import static java.lang.Integer.highestOneBit;
import static java.lang.Integer.min;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.ddd4j.io.Bytes;
import org.ddd4j.io.PooledBytes;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.spi.Ref;
import org.ddd4j.util.collection.Cache;
import org.ddd4j.value.config.ConfKey;

//TODO rename to ResourcePool
@FunctionalInterface
public interface Pool<T extends AutoCloseable> extends Supplier<T> {

	ConfKey<Integer> BUFFER_SIZE = ConfKey.ofInteger("bufferSize", 4096);
	ConfKey<Integer> POOL_SIZE = ConfKey.ofInteger("poolSize", 512);

	Ref<Cache.ReadThrough<Integer, byte[]>> BYTE_ARRAY_CACHE = Ref.of("byteArrayCache",
			ctx -> Cache.<Integer, byte[]>exclusive(b -> b.length)
					.evict()
					.withMaximumCapacity(ctx.conf(POOL_SIZE))
					.lookupValues(Cache.KeyLookup.CEILING, k -> min(bitCount(k) == 1 ? k : highestOneBit(k) << 1, 4096))
					.withFactory(byte[]::new));

	Ref<Cache.ReadThrough<Integer, ByteBuffer>> BYTE_BUFFER_CACHE = Ref.of("byteBufferCache",
			ctx -> ctx.get(BYTE_ARRAY_CACHE).wrapEntries(ByteBuffer::wrap, ByteBuffer::array));

	Ref<Cache.ReadThrough<Integer, Bytes.Arrayed>> BYTES_CACHE = Ref.of("bytesCache",
			ctx -> ctx.get(BYTE_ARRAY_CACHE).wrapEntries(Bytes.Arrayed::new, Bytes.Arrayed::backing));

	Ref<Pool<Bytes>> POOLED_BYTES = Ref.of("pooledBytesPool", ctx -> {
		Cache.Pool<Bytes.Arrayed> pool = ctx.get(BYTES_CACHE).pooledBy(ctx.conf(BUFFER_SIZE));
		return () -> new PooledBytes<>(pool);
	});

	Ref<Pool<WriteBuffer>> BUFFERS = Ref.of(Pool.class, ctx -> {
		Pool<Bytes> bytesPool = ctx.get(POOLED_BYTES);
		return () -> bytesPool.get().buffered();
	});

	default <X> X use(Consumer<? super T> consumer, Function<? super T, ? extends X> result) {
		T closeable = get();
		try {
			consumer.accept(closeable);
			return result.apply(closeable);
		} catch (Exception e) {
			try {
				closeable.close();
			} catch (Exception onClose) {
				e.addSuppressed(onClose);
			}
			throw e;
		}
	}

	default <X> Function<Consumer<T>, X> use(Function<? super T, ? extends X> result) {
		return c -> use(c, result);
	}
}
