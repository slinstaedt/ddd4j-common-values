package org.ddd4j.value.versioned;

import java.util.Arrays;
import java.util.PrimitiveIterator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.ddd4j.contract.Require;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.math.Ordered;

public class Revisions implements Seq<Revision>, Ordered<Revisions> {

	public static final Revisions NONE = new Revisions(0);

	private final long[] offsets;

	public Revisions(int partitionSize) {
		this.offsets = new long[partitionSize];
		Arrays.fill(offsets, Revision.UNKNOWN_OFFSET);
	}

	public Revisions(long[] offsets) {
		this.offsets = Arrays.copyOf(offsets, offsets.length);
	}

	private Revisions(Revisions copy, int partition, long nextOffset) {
		this.offsets = Arrays.copyOf(copy.offsets, copy.offsets.length);
		this.offsets[partition] = nextOffset;
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

	public int partition(int hash) {
		return Math.abs(hash) % offsets.length;
	}

	public boolean reachedBy(Revision revision) {
		return offset(revision.getPartition()) >= revision.getOffset();
	}

	public boolean reachedBy(Revisions revisions) {
		return revisions.stream().allMatch(this::reachedBy);
	}

	public Revisions reset(IntStream partitions) {
		Revisions resetted = this;
		PrimitiveIterator.OfInt iter = partitions.iterator();
		while (iter.hasNext()) {
			resetted = new Revisions(resetted, iter.nextInt(), Revision.UNKNOWN_OFFSET);
		}
		return resetted;
	}

	public Revision revisionOfHash(int hash) {
		return revisionOfPartition(partition(hash));
	}

	public Revision revisionOfPartition(int partition) {
		return new Revision(partition, offset(partition));
	}

	@Override
	public Stream<Revision> stream() {
		return IntStream.range(0, offsets.length).filter(p -> offsets[p] != Revision.UNKNOWN_OFFSET).mapToObj(p -> new Revision(p, offsets[p]));
	}

	public Revisions update(Revision revision) {
		return updateWithPartition(revision.getPartition(), revision.getOffset());
	}

	public Revisions update(Seq<Revision> revisions) {
		return revisions.stream().reduce(this, Revisions::update, Revisions::update);
	}

	public Revisions updateWithHash(int hash, long nextOffset) {
		return updateWithPartition(partition(hash), nextOffset);
	}

	public Revisions updateWithPartition(int partition, long nextOffset) {
		// TODO Require.that(Long.compareUnsigned(nextOffset, offset(partition)) > 0);
		return new Revisions(this, partition, nextOffset);
	}
}
