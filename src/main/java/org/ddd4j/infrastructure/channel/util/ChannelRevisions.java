package org.ddd4j.infrastructure.channel.util;

import java.util.Map;
import java.util.stream.Stream;

import org.ddd4j.infrastructure.ChannelName;
import org.ddd4j.infrastructure.ChannelRevision;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Revisions;

public class ChannelRevisions implements Seq<ChannelRevision> {

	private Map<ChannelName, Revisions> values;

	public void clear() {
		values.clear();
	}

	@Override
	public boolean isEmpty() {
		return values.isEmpty();
	}

	@Override
	public boolean isNotEmpty() {
		return !values.isEmpty();
	}

	public void add(Seq<ChannelRevision> revisions) {
		revisions.forEach(rr -> values.computeIfAbsent(rr.getName(), r -> new Revisions(-1)).update(rr.getRevision()));
	}

	@Override
	public Stream<ChannelRevision> stream() {
		// TODO partition size?
		return values.entrySet().stream().flatMap(e -> e.getValue().stream().map(r -> new ChannelRevision(e.getKey(), r)));
	}

	public void remove(Seq<ChannelRevision> revisions) {
		// TODO remove
		revisions.forEach(rr -> values.computeIfPresent(rr.getName(), (r, rs) -> rs));
	}
}
