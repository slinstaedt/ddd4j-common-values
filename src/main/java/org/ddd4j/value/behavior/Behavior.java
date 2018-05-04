package org.ddd4j.value.behavior;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.ddd4j.aggregate.Aggregates.Aggregate;
import org.ddd4j.util.Require;
import org.ddd4j.util.Throwing;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Revisions;

@FunctionalInterface
public interface Behavior<T> {

	public static class Reference<T> {

		private final Identifier identifier;

		public Reference(Identifier identifier) {
			this.identifier = Require.nonNull(identifier);
		}

		Behavior<T> get() {
			return Behavior.<T> getTrackedEventSource(identifier).map(t -> {
				t.map(Behavior::none).orElseGet(() -> getAggregate(identifier)
						.map(a -> a.map(a2 -> trackedEventSource(a2.getIdentifier(), a2.getVersion(), a2.getState()))));
				return null;
			});
		}
	}

	static <T> Behavior<Optional<T>> getTrackedEventSource(Identifier identifier) {
		return s -> Reaction.accepted(s, s.value(identifier), Seq.empty());
	}

	static <T> Behavior<Optional<Aggregate>> getAggregate(Identifier identifier) {
		return s -> Reaction.accepted(s, s.aggregate(identifier), Seq.empty());
	}

	static <T> Behavior<T> trackedEventSource(Identifier identifier, Revisions expected, T aggregate) {
		return s -> Reaction.accepted(s.track(identifier, expected, aggregate), aggregate, Seq.empty());
	}

	// TODO move to Value subtype?
	static <E, T> Behavior<T> accept(Function<? super E, ? extends T> callback, E event) {
		Require.nonNulls(callback, event);
		return s -> s.record(callback, event);
	}

	static <T> Behavior<T> failed(Exception exception) {
		Require.nonNull(exception);
		return s -> Reaction.failed(s, exception);
	}

	static <T> Behavior<T> none(T unit) {
		return s -> Reaction.accepted(s, unit, Seq.empty());
	}

	static <T> Behavior<T> reject(String message, Object... arguments) {
		Require.nonNulls(message, arguments);
		return s -> Reaction.rejected(s, message, arguments);
	}

	default <E, X> Behavior<X> accept(BiFunction<? super T, ? super E, ? extends X> callback, E event) {
		Require.nonNulls(callback, event);
		return s -> apply(s).mapResult(t -> callback.apply(t, event));
	}

	default <E> Behavior<T> accept(T unit, BiConsumer<? super T, ? super E> callback, E event) {
		Require.nonNulls(callback, event);
		return s -> s.record(e -> {
			callback.accept(unit, event);
			return unit;
		}, event);
	}

	Reaction<T> apply(Session session);

	default Behavior<T> assertContain(Object event) {
		Require.that(outcome().events().contains(event));
		return this;
	}

	default <X extends T> Behavior<X> guard(Class<X> stateType) {
		return map(t -> stateType.isInstance(t) ? Behavior.none(stateType.cast(t)) : reject("wrong state", t, stateType));
	}

	default Behavior<T> guard(Predicate<? super T> condition, String message, Object... arguments) {
		return map(t -> condition.test(t) ? this : reject(message, arguments));
	}

	default <X> Behavior<X> map(Function<? super T, Behavior<X>> nextBehavior) {
		Require.nonNull(nextBehavior);
		return s -> apply(s).mapBehavior(nextBehavior).apply(s);
	}

	default Reaction<T> outcome() {
		return apply(Session.dummy());
	}

	default String rejected(BiFunction<String, Object[], String> formatter) {
		return outcome().foldReaction(t -> null, formatter);
	}

	default T result() {
		return outcome().foldReaction(Function.identity(), Throwing.of(IllegalStateException::new).asBiFunction());
	}
}
