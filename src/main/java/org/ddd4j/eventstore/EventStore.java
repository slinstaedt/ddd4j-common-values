package org.ddd4j.eventstore;

import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Stream;

import org.ddd4j.aggregate.EventBus;
import org.ddd4j.schema.Schema;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Uncommitted;

public interface EventStore {

	interface EventLog<ID, E> {

		default Stream<Committed<ID, E>> commitsSortedByTimestamp() {
			return commits().sorted(Comparator.comparing(Committed::getTimestamp));
		}

		default Stream<Committed<ID, E>> events() {
			return commits().map(Committed::getEntry);
		}
	}

	default <ID, E> Function<Uncommitted<ID, E>, CommitResult<ID, E>> committer(EventBus bus) {
		return attempt -> commit(attempt).visitCommitted(bus::publish);
	}

	<ID, E> EventLog<ID, E> get(Schema<E> eventSchema);
}
