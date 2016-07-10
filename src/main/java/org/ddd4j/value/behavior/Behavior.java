package org.ddd4j.value.behavior;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.collection.Seq;

@FunctionalInterface
public interface Behavior<T> {

	static <T> Behavior<T> accept() {
		return events -> Reaction.accepted(events);
	}

	static <T> Behavior<T> accept(T result) {
		Require.nonNull(result);
		return events -> Reaction.accepted(result, events);
	}

	static <T> Behavior<T> accept(T result, Object event) {
		Require.nonNullElements(result, event);
		return events -> Reaction.accepted(result, events.appendAny().entry(event));
	}

	static <T> Behavior<T> accept(T result, Seq<?> newEvents) {
		Require.nonNullElements(result, newEvents);
		return events -> Reaction.accepted(result, events.appendAny().seq(newEvents));
	}

	static Behavior<Void> guard(boolean condition, String message, Object... arguments) {
		return condition ? Reaction::accepted : reject(message, arguments);
	}

	static Behavior<Void> record(Object event) {
		Require.nonNull(event);
		return events -> Reaction.accepted(events.appendAny().entry(event));
	}

	static <T> Behavior<T> reject(String message, Object... arguments) {
		Require.nonNullElements(message, arguments);
		return events -> Reaction.rejected(message, arguments);
	}

	static <T> Behavior<T> reject(Throwable exception) {
		Require.nonNull(exception);
		return events -> Reaction.rejected(exception.getMessage(), exception);
	}

	Reaction<T> applyEvents(Seq<?> events);

	default Seq<?> changes() {
		return outcome().events();
	}

	default <X> Behavior<X> map(Function<? super T, Behavior<X>> nextBehavior) {
		Require.nonNull(nextBehavior);
		return events -> applyEvents(events).mapBehavior(nextBehavior);
	}

	default Behavior<T> mapEvent(Function<? super T, ?> nextEvent) {
		Behavior<Void> behavior = map(nextEvent.andThen(Behavior::record));
		return behavior.map(v -> accept(result(), behavior.changes()));
	}

	default Behavior<T> mapNothing(Consumer<? super T> consumer) {
		Require.nonNull(consumer);
		return map(t -> {
			consumer.accept(t);
			return this;
		});
	}

	default <X> Behavior<X> mapResult(Function<? super T, X> nextResult) {
		return map(nextResult.andThen(Behavior::accept));
	}

	default Behavior<T> mustContain(Object event) {
		Require.that(outcome().events().contains(event));
		return this;
	}

	default Reaction<T> outcome() {
		return applyEvents(Seq.empty());
	}

	default String rejected(BiFunction<String, Object[], String> formatter) {
		return outcome().fold(t -> null, formatter);
	}

	default T result() {
		return outcome().fold(Function.identity(), Throwing.of(IllegalStateException::new).asBiFunction());
	}
}
