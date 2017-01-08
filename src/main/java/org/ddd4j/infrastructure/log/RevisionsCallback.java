package org.ddd4j.infrastructure.log;

import org.ddd4j.value.versioned.Revisions;

public interface RevisionsCallback {

	Revisions get();

	void set(Revisions revisions);
}
