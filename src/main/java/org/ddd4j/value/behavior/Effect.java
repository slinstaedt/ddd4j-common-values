package org.ddd4j.value.behavior;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import org.ddd4j.messaging.CorrelationIdentifier;
import org.ddd4j.util.Require;
import org.ddd4j.value.Nothing;

@FunctionalInterface
public interface Effect<T, R> {

	@FunctionalInterface
	interface Entity<T, R> extends Effect<T, R> {

		static <T> Effect.Entity<T, Nothing> none(T unit) {
			return Effect.none(unit)::apply;
		}

		default <Y> Effect<T, Y> ask(BiFunction<? super T, ? super CorrelationIdentifier, Effect<T, Y>> callback, Query<Y> query) {
			Require.nonNulls(callback, query);
			return s -> apply(s).mapEffect(callback).apply(s);
		}

		default Effect<T, Nothing> cause(BiConsumer<? super T, ? super CorrelationIdentifier> callback, Object effect) {
			Require.nonNulls(callback, effect);
			return s -> apply(s).mapEffect(callback).apply(s);
		}

		@Override
		default Effect.Entity<T, R> when(Predicate<? super T> condition) {
			return Effect.super.when(condition)::apply;
		}

		@Override
		default Effect.Entity<T, R> when(BiPredicate<? super T, ? super CorrelationIdentifier> condition) {
			return Effect.super.when(condition)::apply;
		}
	}

	@FunctionalInterface
	interface Value<T, R> extends Effect<T, R> {

		static <T> Effect.Value<T, Nothing> none(T unit) {
			return Effect.none(unit)::apply;
		}

		default <X> Effect<X, R> ask(BiFunction<? super T, ? super CorrelationIdentifier, Effect<X, R>> callback, Query<R> query) {
			Require.nonNulls(callback, query);
			return s -> apply(s).mapEffect(callback).apply(s);
		}

		default <X> Effect<X, Nothing> cause(BiFunction<? super T, ? super CorrelationIdentifier, ? extends X> callback, Object effect) {
			Require.nonNulls(callback, effect);
			return s -> apply(s).mapEffect(callback).apply(s);
		}

		@Override
		default Effect.Value<T, R> when(Predicate<? super T> condition) {
			return Effect.super.when(condition)::apply;
		}

		@Override
		default Effect.Value<T, R> when(BiPredicate<? super T, ? super CorrelationIdentifier> condition) {
			return Effect.super.when(condition)::apply;
		}
	}

	/**
	 * Marker interface for query messages.
	 * 
	 * @param <R>
	 *            It's result type
	 */
	interface Query<R> {
	}

	class Dispatched<T, R> {

		private final T result;
		private final CorrelationIdentifier correlation;

		public Dispatched(T result, CorrelationIdentifier correlation) {
			this.result = Require.nonNull(result);
			this.correlation = Require.nonNull(correlation);
		}

		public <X, Y> Effect<X, Y> mapEffect(Function<? super T, Effect<X, Y>> effect) {
			return effect.apply(result);
		}

		public <X, Y> Effect<X, Y> mapEffect(BiFunction<? super T, ? super CorrelationIdentifier, Effect<X, Y>> effect) {
			return effect.apply(result, correlation);
		}
	}

	static <T, R> Effect<T, R> query(Function<? super CorrelationIdentifier, ? extends T> callback, Query<R> query) {
		Require.nonNulls(callback, query);
		return s -> s.send(callback, query);
	}

	static <T> Effect<T, Nothing> command(Function<? super CorrelationIdentifier, ? extends T> callback, Object command) {
		Require.nonNulls(callback, command);
		return s -> s.send(callback, command);
	}

	static <T> Effect<T, Nothing> none(T unit) {
		return s -> new Dispatched<>(unit, CorrelationIdentifier.UNUSED);
	}

	Dispatched<T, R> apply(Session session);

	default <X, Y> Effect<X, Y> map(Function<? super T, Effect<X, Y>> nextEffect) {
		Require.nonNull(nextEffect);
		return s -> apply(s).mapEffect(nextEffect).apply(s);
	}

	default <X> Effect<X, Nothing> transition(Function<? super T, X> nextState) {
		return map(nextState.andThen(Effect::none));
	}

	default Effect<T, R> when(Predicate<? super T> condition) {
		return when((t, c) -> condition.test(t));
	}

	default Effect<T, R> when(BiPredicate<? super T, ? super CorrelationIdentifier> condition) {
		// TODO
		return s -> null;
	}
}
