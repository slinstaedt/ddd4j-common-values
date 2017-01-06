package org.ddd4j.value.versioned;

import org.ddd4j.contract.Require;
import org.ddd4j.io.buffer.ReadBuffer;
import org.ddd4j.io.buffer.WriteBuffer;
import org.ddd4j.value.Value;

public class Revision implements Value<Revision> {

	private final int partition;
	private final long offset;

	public Revision(int partition, long offset) {
		this.partition = partition;
		this.offset = offset;
	}

	public Revision(ReadBuffer buffer) {
		this(buffer.getInt(), buffer.getLong());
	}

	public int getPartition() {
		return partition;
	}

	public long getOffset() {
		return offset;
	}

	public Revision increment(long increment) {
		return next(offset + increment);
	}

	public Revision next(long nextOffset) {
		Require.that(nextOffset > offset);
		return new Revision(partition, nextOffset);
	}

	@Override
	public void serialize(WriteBuffer buffer) {
		buffer.putInt(partition).putLong(offset);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (offset ^ (offset >>> 32));
		result = prime * result + partition;
		return result;
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
}
