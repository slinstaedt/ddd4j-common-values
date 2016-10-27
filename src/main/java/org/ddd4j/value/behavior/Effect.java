package org.ddd4j.value.behavior;

import java.util.function.Function;

import org.ddd4j.aggregate.Identifier;
import org.ddd4j.aggregate.Session;
import org.ddd4j.contract.Require;
import org.ddd4j.messaging.CorrelationID;
import org.ddd4j.value.Nothing;

@FunctionalInterface
public interface Effect<T, R> {

	interface Query<R> {
	}

	class Dispatched<T, R> {

		private final T result;
		private final CorrelationID correlationID;

		public Dispatched(T result, CorrelationID correlationID) {
			this.result = Require.nonNull(result);
			this.correlationID = Require.nonNull(correlationID);
		}

		public <X, Y> Effect<X, Y> mapEffect(Function<? super T, Effect<X, Y>> effect) {
			return effect.apply(result);
		}
	}

	static <T> Effect<T, Nothing> none(T unit) {
		return s -> new Dispatched<>(unit, CorrelationID.create());
	}

	static <T> Effect<T, Nothing> cause(T unit, Identifier target, Object effect) {
		Require.nonNullElements(unit, target, effect);
		return s -> s.send(unit, target, effect);
	}

	static <T, R> Effect<T, R> ofQuery(T unit, Identifier target, Query<R> query) {
		return s -> s.send(unit, target, query);
	}

	static <T> Effect<T, Nothing> ofCommand(T unit, Identifier target, Object command) {
		return s -> s.send(unit, target, command);
	}

	Dispatched<T, R> apply(Session session);

	default <X, Y> Effect<X, Y> map(Function<? super T, Effect<X, Y>> nextEffect) {
		Require.nonNull(nextEffect);
		return s -> apply(s).mapEffect(nextEffect).apply(s);
	}

	default Effect<T, Nothing> mapCommand(Function<? super T, ?> nextCommand) {
	}

	default <X> Effect<X, Nothing> mapResult(Function<? super T, X> nextResult) {
		return map(nextResult.andThen(Effect::none));
	}
}
