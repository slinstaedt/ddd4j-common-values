package org.ddd4j.aggregate;

import java.util.function.Function;

import org.ddd4j.messaging.CorrelationIdentifier;
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

	public <T, R> Dispatched<T, R> send(Function<? super CorrelationIdentifier, ? extends T> callback, Object effect) {
		// TODO Auto-generated method stub
		CorrelationIdentifier correlation = CorrelationIdentifier.UNUSED;
		T result = callback.apply(correlation);
		return new Dispatched<>(result, correlation);
	}

	public <E, T> Reaction<T> record(Function<? super E, ? extends T> callback, E event) {
		T result = callback.apply(event);
		return Reaction.accepted(result, Seq.singleton(event));
	}
}