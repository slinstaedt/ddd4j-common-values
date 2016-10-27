package org.ddd4j.aggregate;

import org.ddd4j.value.behavior.HandlerChain;
import org.ddd4j.value.collection.Seq;

public class Aggretate<T, C, E> {

	private Identifier identifier;
	private Version version;
	private T state;

	private HandlerChain<T> commandHandler;
	private HandlerChain<T> eventHandler;

	public void applyEvent(E event) {
		state = eventHandler.apply(state, event);
	}

	public void applyEvents(Seq<? extends E> events) {
		events.forEach(this::applyEvent);
	}

	public void handleCommand(C command) {
		state = commandHandler.apply(state, command);
	}

	public void commit() {
	}
}
