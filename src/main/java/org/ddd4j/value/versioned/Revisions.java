package org.ddd4j.value.versioned;

import java.util.Arrays;

import org.ddd4j.aggregate.Identifier;
import org.ddd4j.contract.Require;
import org.ddd4j.io.buffer.ReadBuffer;
import org.ddd4j.io.buffer.WriteBuffer;
import org.ddd4j.value.Value;
import org.ddd4j.value.math.Ordered;

public class Revisions extends Value.Simple<Revisions, long[]> implements Ordered<Revisions> {

	public static final Revisions INITIAL = new Revisions(0);

	public static Revisions initial(int partitionSize) {
		return new Revisions(partitionSize);
	}

	private final long[] offsets;

	private Revisions(int partitionSize) {
		this.offsets = new long[partitionSize];
	}

	public Revisions(ReadBuffer buffer) {
		this.offsets = new long[buffer.getInt()];
		for (int i = 0; i < offsets.length; i++) {
			offsets[i] = buffer.getLong();
		}
	}

	private Revisions(Revisions copy, int partition, long offset) {
		Require.that(offset > copy.offsets[partition]);
		this.offsets = Arrays.copyOf(copy.offsets, copy.offsets.length);
		this.offsets[partition] = offset;
	}

	public boolean after(Revisions other) {
		return largerThan(other);
	}

	public boolean before(Revisions other) {
		return smallerThan(other);
	}

	@Override
	public int compareTo(Revisions other) {
		Require.that(this.offsets.length == other.offsets.length);
		int result = 0;
		for (int i = 0; i < offsets.length; i++) {
			result += Long.compareUnsigned(this.offsets[i], other.offsets[i]);
		}
		return Integer.signum(result);
	}

	public Revisions next(Identifier identifier, long nextOffset) {
		return next(partition(identifier), nextOffset);
	}

	public Revisions next(Revision revision) {
		return next(revision.getPartition(), revision.getOffset());
	}

	public Revisions next(int partition, long nextOffset) {
		return new Revisions(this, partition, nextOffset);
	}

	public int partition(Identifier identifier) {
		return identifier.hashCode() % offsets.length;
	}

	public Revision revision(Identifier identifier) {
		int partition = partition(identifier);
		return new Revision(partition, offsets[partition]);
	}

	@Override
	public void serialize(WriteBuffer buffer) {
		buffer.putInt(offsets.length);
		Arrays.stream(offsets).forEachOrdered(buffer::putLong);
	}

	@Override
	protected long[] value() {
		return offsets;
	}
}
