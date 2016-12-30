package org.ddd4j.infrastructure.log;

import java.io.Closeable;

import org.ddd4j.infrastructure.Result;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Committer;
import org.ddd4j.value.versioned.Revision;

public interface Log<E> extends Committer<Seq<E>>, Closeable {

	Result<Committed<Seq<E>>> publisher(Revision startAt, boolean completeOnEnd);

	default Result<Committed<Seq<E>>> readFrom(Revision startAt) {
		return publisher(startAt, true);
	}

	default Result<Committed<Seq<E>>> registerListener(Revision startAt) {
		return publisher(startAt, false);
	}
}
