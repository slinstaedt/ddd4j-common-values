package org.ddd4j.infrastructure.channel.util;

import java.util.Map;
import java.util.stream.Stream;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.ResourceRevision;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Revisions;

public class ResourceRevisions implements Seq<ResourceRevision> {

	private Map<ResourceDescriptor, Revisions> values;

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

	public void add(Seq<ResourceRevision> revisions) {
		revisions.forEach(rr -> values.computeIfAbsent(rr.getResource(), r -> new Revisions(-1)).update(rr.getRevision()));
	}

	@Override
	public Stream<ResourceRevision> stream() {
		// TODO partition size?
		return values.entrySet().stream().flatMap(e -> e.getValue().stream().map(r -> new ResourceRevision(e.getKey(), r)));
	}

	public void remove(Seq<ResourceRevision> revisions) {
		// TODO remove
		revisions.forEach(rr -> values.computeIfPresent(rr.getResource(), (r, rs) -> rs));
	}
}
