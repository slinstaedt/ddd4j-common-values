package org.ddd4j.io.buffer;

import java.util.function.IntFunction;

import org.ddd4j.collection.Array;
import org.ddd4j.collection.Cache;
import org.ddd4j.collection.Cache.Pool;
import org.ddd4j.contract.Require;

public class BytesPool {

	private class PooledBytes extends Bytes {

		private final Array<Bytes> bytes;

		PooledBytes() {
			this.bytes = new Array<>();
		}

		@Override
		public void close() {
			bytes.forEach(pool::release);
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
			bytes.getOrAdd(index % length, pool::acquire).put(index, b);
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
