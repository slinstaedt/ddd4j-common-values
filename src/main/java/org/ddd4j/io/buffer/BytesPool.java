package org.ddd4j.io.buffer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.IntFunction;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Cache;
import org.ddd4j.infrastructure.Cache.Pool;

public class BytesPool {

	private class PooledBytes extends Bytes {

		private final ConcurrentMap<Integer, Bytes> bytes;

		PooledBytes() {
			this.bytes = new ConcurrentHashMap<>();
		}

		@Override
		public void close() {
			bytes.values().forEach(pool::release);
			bytes.clear();
		}

		@Override
		public byte get(int index) {
			return bytes.getOrDefault(index % length, Bytes.NULL).get(index);
		}

		@Override
		public int length() {
			return Integer.MAX_VALUE;
		}

		@Override
		public Bytes put(int index, byte b) {
			bytes.computeIfAbsent(index % length, i -> pool.acquire()).put(index, b);
			return this;
		}
	}

	private final int length;
	private final Pool<Bytes> pool;

	public BytesPool(IntFunction<Bytes> factory, int length) {
		this.length = Require.that(length, length > 0);
		this.pool = Cache.exclusive(Bytes::length).lookupValuesWithEqualKeys().withFactory(Require.nonNull(factory)::apply).pooledBy(length);
	}

	public Bytes newBytes() {
		return new PooledBytes();
	}
}
