package org.ddd4j.infrastructure.channel;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.infrastructure.channel.domain.ChannelPartition;
import org.ddd4j.infrastructure.channel.domain.ChannelRevision;
import org.ddd4j.value.versioned.Revision;

public class ChannelRevisions implements Sequence<ChannelRevision> {

	private final Map<ChannelPartition, Long> values;

	public ChannelRevisions() {
		this.values = new ConcurrentHashMap<>();
	}

	public void add(ChannelName name, Revision revision) {
		values.put(new ChannelPartition(name, revision.getPartition()), revision.getOffset());
	}

	public void add(Sequence<ChannelRevision> revisions) {
		revisions.forEach(r -> add(r.getName(), r.getRevision()));
	}

	public <E> Sequence<E> as(BiFunction<? super String, ? super Integer, E> mapper) {
		return Sequence.of(values.keySet()::stream).map(p -> p.to(mapper));
	}

	public <E> List<E> toList(BiFunction<? super String, ? super Integer, E> mapper) {
		return as(mapper).toList();
	}

	public <K> Map<K, Long> toMap(BiFunction<? super String, ? super Integer, K> mapper) {
		return values.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().to(mapper), Entry::getValue));
	}

	public void clear() {
		values.clear();
	}

	@Override
	public boolean isEmpty() {
		return values.isEmpty();
	}

	public boolean isNotEmpty() {
		return !isEmpty();
	}

	public Sequence<ChannelPartition> partitions() {
		return Sequence.of(values.keySet()::stream);
	}

	public Sequence<ChannelRevision> remove(Sequence<ChannelPartition> partitions) {
		return partitions.map(p -> new ChannelRevision(p, values.getOrDefault(p, Revision.UNKNOWN_OFFSET))).copy().visit(
				r -> values.remove(r.getPartition()));
	}

	@Override
	public Stream<ChannelRevision> stream() {
		return values.entrySet().stream().map(e -> e.getKey().withOffset(e.getValue()));
	}

	public void update(ChannelName name, Revision revision) {
		values.computeIfPresent(new ChannelPartition(name, revision.getPartition()), (p, o) -> revision.getOffset());
	}

	public void update(ChannelRevision revision) {
		update(revision.getName(), revision.getRevision());
	}

	public void update(Sequence<ChannelRevision> revisions) {
		revisions.forEach(this::update);
	}
}
