package org.ddd4j.infrastructure.log;

import org.ddd4j.infrastructure.Result;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Committer;
import org.ddd4j.value.versioned.Revision;

public interface Log<E> extends Committer<E> {

	Result<Committed<E>> publisher(Revision startAt, boolean completeOnEnd);

	default Result<Committed<E>> readFrom(Revision startAt) {
		return publisher(startAt, true);
	}

	default Result<Committed<E>> registerListener(Revision startAt) {
		return publisher(startAt, false);
	}
}
