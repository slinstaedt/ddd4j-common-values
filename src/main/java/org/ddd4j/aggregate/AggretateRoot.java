package org.ddd4j.aggregate;

import org.ddd4j.aggregate.Recorded.Committed;
import org.ddd4j.aggregate.Recorded.Uncommitted;
import org.ddd4j.contract.Require;
import org.ddd4j.value.behavior.Behavior;
import org.ddd4j.value.behavior.HandlerChain;
import org.ddd4j.value.behavior.Reaction;
import org.ddd4j.value.collection.Seq;

public class AggretateRoot<T, C, E> {

	private final Identifier identifier;
	private final Version expected;
	private final HandlerChain<T> commandHandler;
	private final HandlerChain<T> eventHandler;

	private final T state;
	private Seq<Uncommitted<?>> changes;

	public AggretateRoot(Identifier identifier, Version expected, HandlerChain<T> commandHandler, HandlerChain<T> eventHandler, T state) {
		this.identifier = Require.nonNull(identifier);
		this.expected = Require.nonNull(expected);
		this.commandHandler = Require.nonNull(commandHandler);
		this.eventHandler = Require.nonNull(eventHandler);
		this.state = Require.nonNull(state);
		this.changes = Seq.empty();
	}

	public void record(E event) {
		Uncommitted<E> uncommitted = Recorded.uncommitted(identifier, event);
		Behavior<? extends T> behavior = eventHandler.record(state, uncommitted);
		changes = changes.append().entry(uncommitted);
	}

	public Reaction<?> handleCommand(C command) {
		Behavior<? extends T> behavior = commandHandler.record(state, command);
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
