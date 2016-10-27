package org.ddd4j.value.behavior;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.ddd4j.aggregate.Session;
import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.collection.Seq;

@FunctionalInterface
public interface Behavior<T> {

	static <E, T> Behavior<T> accept(Function<? super E, ? extends T> handler, E event) {
		Require.nonNullElements(handler, event);
		return s -> s.record(handler, event);
	}

	static <T> Behavior<T> failed(Exception exception) {
		Require.nonNull(exception);
		return s -> Reaction.failed(exception);
	}

	static <T> Behavior<T> none(T unit) {
		return s -> Reaction.accepted(unit, Seq.empty());
	}

	static <T> Behavior<T> reject(String message, Object... arguments) {
		Require.nonNullElements(message, arguments);
		return s -> Reaction.rejected(message, arguments);
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

	default <E, X> Behavior<X> handle(BiFunction<? super T, ? super E, X> handler, E event) {
		return mapResult(t -> handler.apply(t, event));
	}

	default <X> Behavior<X> map(Function<? super T, Behavior<X>> nextBehavior) {
		Require.nonNull(nextBehavior);
		return s -> apply(s).mapBehavior(nextBehavior).apply(s);
	}

	default Behavior<T> mapEvent(Function<? super T, ?> nextEvent) {
		return map(t -> nextEvent.andThen(e -> Behavior.accept(m -> t, e)).apply(t));
	}

	default Behavior<T> mapNothing(Consumer<? super T> nextConsumer) {
		Require.nonNull(nextConsumer);
		return map(t -> {
			nextConsumer.accept(t);
			return this;
		});
	}

	default <X> Behavior<X> mapResult(Function<? super T, X> nextResult) {
		return map(nextResult.andThen(Behavior::none));
	}

	default Reaction<T> outcome() {
		return apply(Session.create());
	}

	default String rejected(BiFunction<String, Object[], String> formatter) {
		return outcome().foldReaction(t -> null, formatter);
	}

	default T result() {
		return outcome().foldReaction(Function.identity(), Throwing.of(IllegalStateException::new).asBiFunction());
	}
}
