package org.ddd4j.infrastructure.channel.util;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.channel.domain.ChannelPartition;
import org.ddd4j.infrastructure.channel.domain.ChannelRevision;

public class ChannelRevisions implements Sequence<ChannelRevision> {

	private final Map<ChannelPartition, Long> values;

	public ChannelRevisions() {
		this.values = new ConcurrentHashMap<>();
	}

	public void add(Sequence<ChannelRevision> revisions) {
		revisions.forEach(r -> values.put(r.getPartition(), r.getOffset()));
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

	public void remove(Sequence<ChannelPartition> partitions) {
		partitions.forEach(values::remove);
	}

	@Override
	public Stream<ChannelRevision> stream() {
		return values.entrySet().stream().map(e -> e.getKey().withOffset(e.getValue()));
	}

	public void update(ChannelRevision revision) {
		values.computeIfPresent(revision.getPartition(), (p, o) -> revision.getOffset());
	}

	public void update(Sequence<ChannelRevision> revisions) {
		revisions.forEach(this::update);
	}
}
