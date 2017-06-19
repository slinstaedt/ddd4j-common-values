package org.ddd4j.value.versioned;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.ddd4j.Require;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.math.Ordered.Comparison;

//TODO move to log package
public class Revisions {

	public static final Revisions NONE = new Revisions(0);

	public static Revisions create(int partitionSize, IntStream partitions, long offset) {
		Revisions revisions = new Revisions(partitionSize);
		partitions.forEach(p -> revisions.updateWithPartition(p, offset));
		return revisions;
	}

	private final long[] offsets;

	public Revisions(int partitionSize) {
		Require.that(partitionSize >= 0);
		this.offsets = new long[partitionSize];
		Arrays.fill(offsets, Revision.UNKNOWN_OFFSET);
	}

	public Revisions(long[] offsets) {
		this.offsets = Arrays.copyOf(offsets, offsets.length);
	}

	public Revisions(Revisions copy) {
		this(copy.offsets);
	}

	public Revisions(Seq<Revision> revisions) {
		this(revisions.stream().mapToInt(Revision::getPartition).max().orElse(0));
		revisions.forEach(r -> offsets[r.getPartition()] = r.getOffset());
	}

	public Comparison compare(Revision revision) {
		return revisionOfPartition(revision.getPartition()).compare(revision);
	}

	public Stream<Revision> diffOffsetsFrom(Revisions other) {
		return partitions().filter(p -> this.offsets[p] != other.offsets[p]).mapToObj(this::revisionOfPartition);
	}

	public int getPartitionSize() {
		return offsets.length;
	}

	public boolean hasOffset(int partition) {
		return offsets[partition] != Revision.UNKNOWN_OFFSET;
	}

	public boolean isNonePartitionOffsetKnown() {
		return partitions().count() == 0;
	}

	public boolean isPartitionSizeKnown() {
		return getPartitionSize() > 0;
	}

	public long offset(int partition) {
		long offset = offsets[partition];
		Require.that(offset != Revision.UNKNOWN_OFFSET);
		return offset;
	}

	public int partition(int hash) {
		return Math.abs(hash) % offsets.length;
	}

	public IntStream partitions() {
		return IntStream.range(0, offsets.length).filter(p -> offsets[p] != Revision.UNKNOWN_OFFSET);
	}

	public long reset(int partition) {
		return updateWithPartition(partition, Revision.UNKNOWN_OFFSET);
	}

	public void reset(IntStream partitions) {
		partitions.forEach(this::reset);
	}

	public Revision revisionOfHash(int hash) {
		return revisionOfPartition(partition(hash));
	}

	public Revision revisionOfPartition(int partition) {
		return new Revision(partition, offset(partition));
	}

	public Stream<Revision> revisions() {
		return partitions().mapToObj(this::revisionOfPartition);
	}

	public Stream<Revision> revisionsOfPartitions(IntStream partitions) {
		return partitions.mapToObj(this::revisionOfPartition);
	}

	public long update(Revision revision) {
		return updateWithPartition(revision.getPartition(), revision.getOffset());
	}

	public void update(Stream<Revision> revisions) {
		revisions.forEachOrdered(this::update);
	}

	public void updateIfEarlier(Revisions other) {
		partitions().filter(p -> this.offset(p) > other.offset(p)).forEach(p -> updateWithPartition(p, other.offset(p)));
	}

	public void updateIfLater(Revision other) {
		int partition = other.getPartition();
		if (!hasOffset(partition) || offset(partition) < other.getOffset()) {
			update(other);
		}
	}

	public long updateWithHash(int hash, long nextOffset) {
		return updateWithPartition(partition(hash), nextOffset);
	}

	public long updateWithPartition(int partition, long nextOffset) {
		long value = offsets[partition];
		offsets[partition] = nextOffset;
		return value;
	}
}
