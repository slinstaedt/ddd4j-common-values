package org.ddd4j.log;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.value.versioned.Revision;

public interface RevisionsCallback {

	Promise<Stream<Revision>> loadRevisions(IntStream partitions);

	Promise<Void> saveRevisions(Stream<Revision> revisions);
}
