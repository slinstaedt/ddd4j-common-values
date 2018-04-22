package org.ddd4j.value.behavior;

import org.ddd4j.aggregate.Aggregates;
import org.ddd4j.value.Named;

public interface AggregateType<ID extends Identifier, T, C, E> extends Named {

	private Aggregates aggregates;
	private HandlerChain<T> commandHandler;
	private HandlerChain<T> eventHandler;

	T applyEvent(T aggregate, E event);

	Behavior<T> processCommand(T aggregate, C command);
}
