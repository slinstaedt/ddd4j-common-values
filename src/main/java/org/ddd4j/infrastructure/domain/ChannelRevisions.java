package org.ddd4j.infrastructure.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelPartition;
import org.ddd4j.infrastructure.domain.value.ChannelRevision;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Position;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;

public class ChannelRevisions implements Sequence<ChannelRevision> {

	private static class Offsets {

		private final AtomicReference<long[]> values;

		public Offsets() {
			this.values = new AtomicReference<>(new long[0]);
		}

		public void add(Revision revision) {
			long[] offsets;
			while (revision.getPartition() >= (offsets = values.get()).length) {
				long[] arr = Arrays.copyOf(offsets, revision.getPartition() + 1);
				Arrays.fill(arr, revision.getPartition(), arr.length, Revision.UNKNOWN_OFFSET);
				values.compareAndSet(offsets, arr);
			}
			set(revision.getPartition(), revision.getOffset());
		}

		public int getPartitionSize() {
			return values.get().length;
		}

		public Revisions getRevisions() {
			return new Revisions(values.get());
		}

		public boolean isKnown(int partition) {
			return map(partition, a -> a[partition] != Revision.UNKNOWN_OFFSET, Boolean.FALSE);
		}

		public IntStream knownPartitions() {
			return map(-1, a -> IntStream.range(0, a.length).filter(i -> a[i] != Revision.UNKNOWN_OFFSET), IntStream.empty());
		}

		public Stream<Revision> knownRevisions() {
			return map(-1, a -> knownPartitions().mapToObj(p -> new Revision(p, a[p])), Stream.empty());
		}

		private <R> R map(int partition, Function<long[], R> function, R defaultValue) {
			long[] offsets = values.get();
			return partition < offsets.length ? function.apply(offsets) : defaultValue;
		}

		public long offset(int partition) {
			return map(partition, a -> a[partition], Revision.UNKNOWN_OFFSET);
		}

		public Offsets remove(int partition) {
			if (isKnown(partition)) {
				set(partition, Revision.UNKNOWN_OFFSET);
			}
			return LongStream.of(values.get()).anyMatch(o -> o != Revision.UNKNOWN_OFFSET) ? this : null;
		}

		public Revision revision(int partition) {
			return new Revision(partition, offset(partition));
		}

		private void set(int partition, long offset) {
			values.get()[partition] = offset;
		}

		public Position update(Committed<?, ?> committed) {
			Position position = committed.position(this::revision);
			if (position == Position.UPTODATE) {
				Revision next = committed.getNextExpected();
				set(next.getPartition(), next.getOffset());
			}
			return position;
		}
	}

	private final Map<ChannelName, Offsets> values;

	public ChannelRevisions() {
		this.values = new ConcurrentHashMap<>();
	}

	public void add(ChannelName name, Revision revision) {
		values.computeIfAbsent(name, n -> new Offsets()).add(revision);
	}

	public void add(ChannelRevision revision) {
		add(revision.getName(), revision.getRevision());
	}

	public void add(Sequence<ChannelRevision> revisions) {
		revisions.forEach(this::add);
	}

	public <E> Sequence<E> as(BiFunction<? super String, ? super Integer, E> mapper) {
		return partitions().map(p -> p.to(mapper));
	}

	public void clear() {
		values.clear();
	}

	private <R> R map(ChannelName name, Function<Offsets, R> function, R defaultValue) {
		Offsets offsets = values.get(name);
		return offsets != null ? function.apply(offsets) : defaultValue;
	}

	public long offset(ChannelPartition partition) {
		return map(partition.getName(), o -> o.offset(partition.getPartition()), Revision.UNKNOWN_OFFSET);
	}

	public Sequence<ChannelPartition> partitions() {
		return Sequence.of(() -> values.entrySet().stream().flatMap(
				e -> e.getValue().knownPartitions().mapToObj(p -> new ChannelPartition(e.getKey(), p))));
	}

	public Sequence<ChannelRevision> remove(Sequence<ChannelPartition> partitions) {
		return partitions.map(p -> new ChannelRevision(p, offset(p))).copy().visit(
				r -> values.computeIfPresent(r.getName(), (n, o) -> o.remove(r.getPartitionAsInteger())));
	}

	public Sequence<ChannelRevision> revision(ChannelName name, Committed<?, ?> committed) {
		return map(name, o -> Sequence.of(new ChannelRevision(name, o.revision(committed.getActual().getPartition()))), Sequence.empty());
	}

	public Revisions revisions(ChannelName name) {
		return map(name, Offsets::getRevisions, Revisions.NONE);
	}

	@Override
	public int size() {
		return values.values().stream().mapToInt(Offsets::getPartitionSize).sum();
	}

	@Override
	public Stream<ChannelRevision> stream() {
		return values.entrySet().stream().flatMap(e -> e.getValue().knownRevisions().map(r -> new ChannelRevision(e.getKey(), r)));
	}

	public <E> List<E> toList(BiFunction<? super String, ? super Integer, E> mapper) {
		return as(mapper).toList();
	}

	public <K> Map<K, Long> toMap(BiFunction<? super String, ? super Integer, K> mapper) {
		return stream().collect(Collectors.toMap(rev -> rev.to(mapper), ChannelRevision::getOffset));
	}

	public Position tryUpdate(ChannelName name, Committed<?, ?> committed) {
		return map(name, o -> o.update(committed), Position.FAILED);
	}
}
