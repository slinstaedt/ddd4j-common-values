package org.ddd4j.value.versioned;

import org.ddd4j.Require;
import org.ddd4j.io.Bytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.value.Value;
import org.ddd4j.value.math.Ordered;

public class Revision implements Value<Revision>, Ordered<Revision> {

	public static final long END_OFFSET = Long.MAX_VALUE;
	public static final long UNKNOWN_OFFSET = -1;
	public static final Revision UNKNOWN = new Revision(0, UNKNOWN_OFFSET);

	public static Revision deserialize(byte[] bytes) {
		return deserialize(Bytes.wrap(bytes).buffered());
	}

	public static Revision deserialize(ReadBuffer buffer) {
		return new Revision(buffer.getInt(), buffer.getLong());
	}

	private final int partition;
	private final long offset;

	public Revision(int partition, long offset) {
		this.partition = Require.that(partition, partition >= 0);
		this.offset = Require.that(offset, offset != UNKNOWN_OFFSET);
	}

	public Revision checkPartition(int partition) {
		Require.that(this.partition == partition);
		return this;
	}

	public Position comparePosition(Revision other) {
		if (this.offset != UNKNOWN_OFFSET && other.offset != UNKNOWN_OFFSET) {
			return Position.of(this.compareTo(other));
		} else {
			return Position.FAILED;
		}
	}

	@Override
	public int compareTo(Revision other) {
		Require.that(this.partition == other.partition);
		return Long.compareUnsigned(this.offset, other.offset);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Revision other = (Revision) obj;
		if (offset != other.offset) {
			return false;
		}
		if (partition != other.partition) {
			return false;
		}
		return true;
	}

	public long getOffset() {
		return offset;
	}

	public int getPartition() {
		return partition;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (offset ^ (offset >>> 32));
		result = prime * result + partition;
		return result;
	}

	public Revision increment(int increment) {
		return next(offset + increment);
	}

	public boolean isEnd() {
		return offset == END_OFFSET;
	}

	public Revision next(long nextOffset) {
		Require.that(nextOffset > offset);
		return new Revision(partition, nextOffset);
	}

	@Override
	public WriteBuffer serialize(WriteBuffer buffer) {
		return buffer.putInt(partition).putLong(offset);
	}

	public byte[] toBytes() {
		byte[] bytes = new byte[12];
		serialize(Bytes.wrap(bytes).buffered());
		return bytes;
	}
}
