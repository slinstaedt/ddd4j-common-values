package org.ddd4j.value.behavior;

import java.util.Optional;
import java.util.function.Function;

import org.ddd4j.aggregate.Aggregates;
import org.ddd4j.aggregate.Aggregates.Aggregate;
import org.ddd4j.messaging.CorrelationIdentifier;
import org.ddd4j.util.Require;
import org.ddd4j.value.collection.Map;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revisions;
import org.ddd4j.value.versioned.Uncommitted;

public class Session {

	private static class Tracked<T> {

		private final Identifier identifier;
		private final Revisions expected;
		private final T state;
		private final Seq<?> changes;

		Tracked(Identifier identifier, Revisions expected) {
			this(identifier, expected, null, Seq.empty());
		}

		Tracked(Identifier identifier, Revisions expected, T state) {
			this(identifier, expected, state, Seq.empty());
		}

		private Tracked(Identifier identifier, Revisions expected, T state, Seq<?> changes) {
			this.identifier = Require.nonNull(identifier);
			this.expected = Require.nonNull(expected);
			this.state = Require.nonNull(state);
			this.changes = Require.nonNull(changes);
		}

		public <E, X> Tracked<X> record(E event, Function<Uncommitted<Identifier, E>, X> handler) {
			X updatedState = handler.apply(Recorded.uncommitted(identifier, event));
			return new Tracked<>(identifier, expected, updatedState, changes.appendAny().entry(event));
		}
	}

	private final Identifier current;
	private final Aggregates aggregates;
	private final Map<Identifier, Tracked<?>> tracked;

	public Session(Identifier current, Aggregates aggregates) {
		this(current, aggregates, Map.empty());
	}

	private Session(Identifier currentAggregate, Aggregates aggregates, Map<Identifier, Tracked<?>> tracked) {
		this.current = Require.nonNull(currentAggregate);
		this.aggregates = Require.nonNull(aggregates);
		this.tracked = Require.nonNull(tracked);
	}

	public <T, R> Dispatched<T, R> send(Function<? super CorrelationIdentifier, ? extends T> callback, Object effect) {
		// TODO Auto-generated method stub
		CorrelationIdentifier correlation = CorrelationIdentifier.UNUSED;
		T result = callback.apply(correlation);
		return new Dispatched<>(result, correlation);
	}

	public Session track(Identifier identifier, Revisions expected, Object aggregate) {
		Require.that(!tracked.containsKey(identifier));
		return new Session(identifier, aggregates, tracked.updated(identifier, new Tracked<>(identifier, expected, aggregate)));
	}

	public <E, T> Reaction<T> record(Function<? super E, ? extends T> handler, E event) {
		Tracked<?> originalState = tracked.get(current).orElse(new Tracked<>(current, Revisions.INITIAL));
		Tracked<? extends T> updatedState = originalState.record(event, handler.compose(Uncommitted::getPayload));
		Session session = new Session(current, aggregates, tracked.updated(current, updatedState));
		return Reaction.accepted(session, updatedState.state, Seq.singleton(event));
	}

	public <T> Optional<T> value(Identifier identifier) {
		return tracked.get(identifier).mapOptional(t -> t.state).map(mapper);
	}

	public Optional<Aggregate> aggregate(Identifier identifier) {
		return aggregates.get(identifier);
	}
}