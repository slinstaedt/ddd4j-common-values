package org.ddd4j.aggregate;

import java.util.function.Function;

import org.ddd4j.value.behavior.Effect.Dispatched;
import org.ddd4j.value.behavior.Reaction;
import org.ddd4j.value.collection.Map;
import org.ddd4j.value.collection.Seq;

public class Session {

	public static Session create() {
		// TODO Auto-generated method stub
		return new Session();
	}

	private Map<Identifier, Aggretate<?, ?, ?>> aggregates;

	public Session() {
	}

	public <T, R> Dispatched<T, R> send(T unit, Identifier targetIdentifier, Object effect) {
		// TODO Auto-generated method stub
		return null;
	}

	public <E, T> Reaction<T> record(Function<? super E, ? extends T> handler, E event) {
		T result = handler.apply(event);
		return Reaction.accepted(result, Seq.singleton(event));
	}
}