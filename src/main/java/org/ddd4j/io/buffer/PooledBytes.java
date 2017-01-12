package org.ddd4j.io.buffer;

import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.ddd4j.collection.Cache;
import org.ddd4j.contract.Require;

public class PooledBytes extends Bytes {

	private final Cache.Pool<Bytes> pool;
	private final NavigableMap<Integer, Bytes> bytes;

	public PooledBytes(Cache.Pool<Bytes> pool) {
		this.pool = Require.nonNull(pool);
		this.bytes = new TreeMap<>();
	}

	private Bytes partition(int index, boolean create) {
		Entry<Integer, Bytes> entry = null;
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

	@Override
	public Bytes put(int index, byte b) {
		partition(index, true).put(index, b);
		return this;
	}
}