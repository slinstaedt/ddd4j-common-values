package org.ddd4j.infrastructure.log;

import org.ddd4j.infrastructure.Result;
import org.ddd4j.value.Throwing.TClosable;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Committer;
import org.ddd4j.value.versioned.Revisions;

public interface Log<E> extends Committer<Seq<E>>, TClosable {

	Revisions currentRevisions() throws Exception;

	Result<Committed<Seq<E>>> publisher(Revisions startAt, boolean completeOnEnd);

	default Result<Committed<Seq<E>>> readFrom(Revisions startAt) {
		return publisher(startAt, true);
	}

	default Result<Committed<Seq<E>>> registerListener(Revisions startAt) {
		return publisher(startAt, false);
	}
}
