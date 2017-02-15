package org.ddd4j.infrastructure.channel;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.ddd4j.value.versioned.Revision;

public interface RevisionsCallback {

	Stream<Revision> loadRevisions(IntStream partitions);

	void saveRevisions(Stream<Revision> revisions);
}
