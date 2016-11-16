package org.ddd4j.messaging;

import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Stream;

import org.ddd4j.aggregate.EventBus;
import org.ddd4j.aggregate.Identifier;
import org.ddd4j.aggregate.Recorded.CommitResult;
import org.ddd4j.aggregate.Recorded.Committed;
import org.ddd4j.aggregate.Recorded.Uncommitted;
import org.ddd4j.aggregate.Version;
import org.ddd4j.schema.Schema;

public interface EventStore {

	interface AggregateLog<E> {

		@SuppressWarnings("unchecked")
		default <X> AggregateLog<X> casted(Identifier identifier) {
			if (getIdentifier().equals(identifier)) {
				return (AggregateLog<X>) this;
			} else {
				throw new IllegalArgumentException();
			}
		}

		Version lastCommittedVersion();

		CommitResult<E> tryCommit(Uncommitted<E> attempt);

		Stream<Committed<E>> commits();

		default Stream<E> events() {
			return eventsSince(Version.INITIAL);
		}

		default Stream<E> eventsSince(Version since) {
			return commits().filter(c -> c.after(since)).flatMap(Committed::events);
		}

		Identifier getIdentifier();
	}

	interface EventLog<E> {

		Comparator<Committed<?>> ORDER_BY_TIMESTAMP = (c1, c2) -> c1.getTimestamp().compareTo(c2.getTimestamp());

		default Stream<Committed<E>> commits() {
			return logs().flatMap(AggregateLog::commits);
		}

		default Stream<Committed<E>> commitsSortedByTimestamp() {
			return commits().sorted(ORDER_BY_TIMESTAMP);
		}

		default Stream<E> events() {
			return commits().map(Committed::getEvent);
		}

		Class<E> getEventType();

		Stream<AggregateLog<E>> logs();

		AggregateLog<E> of(Identifier identifier);
	}

	default <E> CommitResult<E> commit(Uncommitted<E> attempt) {
		return get(attempt.getEventSource()).commit(attempt);
	}

	default <E> Function<Uncommitted<E>, CommitResult<E>> committer(EventBus bus) {
		return attempt -> commit(attempt).visitCommitted(bus::publish);
	}

	default <E> Stream<E> events(Identifier identifier) {
		return get(identifier).events();
	}

	default <E> Stream<E> events(Identifier identifier, Version since) {
		return get(identifier).eventsSince(since);
	}

	<E> EventLog<E> get(Schema eventSchema);

	default <E> AggregateLog<E> get(Identifier identifier) {
		return get(identifier.getEventType()).of(identifier);
	}
}
