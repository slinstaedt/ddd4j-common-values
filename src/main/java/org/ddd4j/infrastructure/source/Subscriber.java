package org.ddd4j.infrastructure.source;

import org.ddd4j.io.buffer.Bytes;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revisions;

public interface Subscriber {

	Revisions loadRevisions();

	void onCommitted(Committed<Bytes, Bytes> committed);

	void saveRevisions(Revisions revisions);
}
