package org.ddd4j.infrastructure.pipe;

import org.ddd4j.io.buffer.Bytes;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public interface Subscriber {

	Seq<Revision> loadRevisions();

	void onCommitted(Committed<Bytes, Bytes> committed);

	void saveRevisions(Seq<Revision> revisions);
}
