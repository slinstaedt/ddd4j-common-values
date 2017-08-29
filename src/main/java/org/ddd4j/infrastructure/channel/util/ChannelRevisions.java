package org.ddd4j.infrastructure.channel.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.ddd4j.infrastructure.Sequence;
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

	public <V> Sequence<V> as(BiFunction<? super String, ? super Integer, V> mapper) {
		return Sequence.of(values.keySet()::stream).map(p -> p.as(mapper));
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
