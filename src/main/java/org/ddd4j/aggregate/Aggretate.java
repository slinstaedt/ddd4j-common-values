package org.ddd4j.aggregate;

import java.util.function.Function;

import org.ddd4j.aggregate.Recorded.Uncommitted;
import org.ddd4j.value.behavior.Behavior;
import org.ddd4j.value.behavior.HandlerChain;
import org.ddd4j.value.collection.Seq;

public class Aggretate<T, C, E> {

	private Identifier identifier;
	private Version expected;
	private T state;

	private Seq<?> changes;

	private HandlerChain<T> commandHandler;
	private HandlerChain<T> eventHandler;

	public <Event extends E> void record(Function<Uncommitted<Event>, ? extends T> handler, Event event) {
		state = handler.apply(Recorded.uncommitted(identifier, expected, event));
	}

	public void applyEvent(E event) {
		Behavior<? extends T> behavior = eventHandler.handle(state, event);
	}

	public void handleCommand(C command) {
		Behavior<? extends T> behavior = commandHandler.handle(state, command);
	}

	public void replayFromHistory(Seq<? extends E> events) {
		events.forEach(this::applyEvent);
	}
}
