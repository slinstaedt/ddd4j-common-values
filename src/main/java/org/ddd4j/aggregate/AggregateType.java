package org.ddd4j.aggregate;

import java.util.function.Function;

import org.ddd4j.eventstore.EventStore;
import org.ddd4j.value.behavior.Behavior;
import org.ddd4j.value.behavior.HandlerChain;
import org.ddd4j.value.behavior.Reaction;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Uncommitted;

public class AggregateType<ID extends Identifier, T, C, E> {

	@FunctionalInterface
	public interface Modify<T> {

		<X> X apply(Function<? super T, Behavior<X>> function);
	}

	private EventStore eventStore;
	private Aggregates aggregates;
	private HandlerChain<T> commandHandler;
	private HandlerChain<T> eventHandler;

	private Seq<Uncommitted<E>> changes;

	public void record(ID identifier, E event) {
		Uncommitted<E> uncommitted = Recorded.uncommitted(identifier, event);
		Behavior<? extends T> behavior = eventHandler.record(state, uncommitted);
		changes = changes.append().entry(uncommitted);
	}

	public Reaction<?> handleCommand(T value, C command) {
		Behavior<? extends T> behavior = commandHandler.record(value, command);
		// TODO
		return null;
	}

	public void applyEvent(Committed<? extends E> event) {
		// TODO
	}

	public void applyFromHistory(Seq<Committed<? extends E>> events) {
		events.forEach(this::applyEvent);
	}
}
