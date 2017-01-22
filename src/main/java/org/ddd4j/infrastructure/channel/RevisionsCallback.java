package org.ddd4j.infrastructure.channel;

import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Revision;

public interface RevisionsCallback {

	Seq<Revision> loadRevisions(int[] partitions);

	void saveRevisions(Seq<Revision> revisions);
}
