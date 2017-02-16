package org.ddd4j.value.versioned;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.ddd4j.contract.Require;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.math.Ordered.Comparison;

//TODO move to log package
public class Revisions {

	public static final Revisions NONE = new Revisions(0);

	private final long[] offsets;

	public Revisions(int partitionSize) {
		Require.that(partitionSize >= 0);
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

	public Comparison compare(Revision revision) {
		return Comparison.of(Long.signum(offset(revision.getPartition()) - revision.getOffset()));
	}

	public boolean isPartitionSizeKnown() {
		return getPartitionSize() > 0;
	}

	public int getPartitionSize() {
		return offsets.length;
	}

	public long offset(int partition) {
		long offset = offsets[partition];
		Require.that(offset != Revision.UNKNOWN_OFFSET);
		return offset;
	}

	public int partition(int hash) {
		return Math.abs(hash) % offsets.length;
	}

	public void reset(IntStream partitions) {
		partitions.forEach(p -> updateWithPartition(p, Revision.UNKNOWN_OFFSET));
	}

	public Revision revisionOfHash(int hash) {
		return revisionOfPartition(partition(hash));
	}

	public Revision revisionOfPartition(int partition) {
		return new Revision(partition, offset(partition));
	}

	public Stream<Revision> revisionsOfPartitions(IntStream partitions) {
		return partitions.mapToObj(this::revisionOfPartition);
	}

	public Stream<Revision> stream() {
		return IntStream.range(0, offsets.length)
				.filter(p -> offsets[p] != Revision.UNKNOWN_OFFSET)
				.mapToObj(p -> new Revision(p, offsets[p]));
	}

	public void update(Revision revision) {
		updateWithPartition(revision.getPartition(), revision.getOffset());
	}

	public void update(Stream<Revision> revisions) {
		revisions.forEachOrdered(this::update);
	}

	public void updateWithHash(int hash, long nextOffset) {
		updateWithPartition(partition(hash), nextOffset);
	}

	public void updateWithPartition(int partition, long nextOffset) {
		// TODO Require.that(Long.compareUnsigned(nextOffset, offset(partition)) > 0);
		offsets[partition] = nextOffset;
	}

	public void updateWithPartitions(IntStream partitions, long nextOffset) {
		partitions.forEach(p -> updateWithPartition(p, nextOffset));
	}
}
