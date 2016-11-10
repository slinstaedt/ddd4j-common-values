package org.ddd4j.messaging;

import org.ddd4j.aggregate.Identifier;
import org.ddd4j.aggregate.Recorded.Committed;
import org.ddd4j.aggregate.Version;
import org.ddd4j.value.collection.Seq;

public interface EventStore {

	class Commit {

		private Identifier source;
		private Version expected;
		private Seq<?> events;
	}

	class OptimisticLockingException extends RuntimeException {
	}

	void commit(Commit attempt);

	Seq<Committed<?>> load(Identifier source);

	default Seq<Committed<?>> buildCommittedEvents(Commit commit, Version commitVersion) {
		return null;
	}
}
