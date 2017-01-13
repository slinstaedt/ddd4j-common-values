package org.ddd4j.infrastructure.pipe;

import org.ddd4j.io.buffer.ReadBuffer;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public interface Subscriber extends org.reactivestreams.Subscriber<Committed<ReadBuffer, ReadBuffer>> {

	Seq<Revision> loadRevisions(int[] partitions);

	void saveRevisions(Seq<Revision> revisions);
}
