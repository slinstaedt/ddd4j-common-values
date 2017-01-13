package org.ddd4j.value.versioned;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.ddd4j.contract.Require;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.math.Ordered;

//TODO move to infrastructure?
public class Revisions implements Seq<Revision>, Ordered<Revisions> {

	private final long[] offsets;

	public Revisions(int partitionSize) {
		this.offsets = new long[partitionSize];
		Arrays.fill(offsets, Revision.UNKNOWN_OFFSET);
	}

	public Revisions(long[] offsets) {
		this.offsets = Arrays.copyOf(offsets, offsets.length);
	}

	public Revisions(Seq<Revision> revisions) {
		this(revisions.stream().mapToInt(Revision::getPartition).max().orElse(0));
		revisions.forEach(r -> offsets[r.getPartition()] = r.getOffset());
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

	public long offset(int partition) {
		long offset = offsets[partition];
		Require.that(offset != Revision.UNKNOWN_OFFSET);
		return offset;
	}

	public int partition(Object key) {
		return Math.abs(key.hashCode()) % offsets.length;
	}

	public boolean reachedBy(Revision revision) {
		return offset(revision.getPartition()) >= revision.getOffset();
	}

	public boolean reachedBy(Revisions revisions) {
		return revisions.stream().allMatch(this::reachedBy);
	}

	public Revision revision(int partition) {
		return new Revision(partition, offset(partition));
	}

	public Revision revision(Object key) {
		return revision(partition(key));
	}

	public Stream<Revision> revisions(Stream<Integer> partitions) {
		return partitions.mapToInt(Integer::intValue).mapToObj(this::revision);
	}

	@Override
	public Stream<Revision> stream() {
		return IntStream.range(0, offsets.length).filter(p -> offsets[p] != Revision.UNKNOWN_OFFSET).mapToObj(p -> new Revision(p, offsets[p]));
	}

	public void update(int partition, long nextOffset) {
		Require.that(Long.compareUnsigned(nextOffset, offset(partition)) > 0);
		this.offsets[partition] = nextOffset;
	}

	public void update(Object key, long nextOffset) {
		update(partition(key), nextOffset);
	}

	public void update(Revision revision) {
		update(revision.getPartition(), revision.getOffset());
	}
}
