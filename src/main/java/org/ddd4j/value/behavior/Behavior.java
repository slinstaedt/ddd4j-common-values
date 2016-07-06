package org.ddd4j.value.behavior;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.behavior.Reaction.Accepted;
import org.ddd4j.value.behavior.Reaction.AcceptedWithResult;
import org.ddd4j.value.behavior.Reaction.Rejected;
import org.ddd4j.value.collection.Seq;

@FunctionalInterface
public interface Behavior<T> {

	static <T> Behavior<T> accept(T result) {
		Require.nonNull(result);
		return (events) -> new AcceptedWithResult<>(events, result);
	}

	static <T> Behavior<T> accept(T result, Object event) {
		Require.nonNullElements(result, event);
		return (events) -> new AcceptedWithResult<>(events.appendAny().entry(event), result);
	}

	static <T> Behavior<T> accept(T result, Seq<?> newEvents) {
		Require.nonNullElements(result, newEvents);
		return (events) -> new AcceptedWithResult<>(events.appendAny().seq(newEvents), result);
	}

	static Behavior<Void> guard(boolean condition, String message, Object... arguments) {
		if (condition) {
			return (events) -> new Accepted<>(events);
		} else {
			return reject(message, arguments);
		}
	}

	static Behavior<Void> record(Object event) {
		Require.nonNull(event);
		return (events) -> new Accepted<>(events.appendAny().entry(event));
	}

	static <T> Behavior<T> reject(String message, Object... arguments) {
		Require.nonNullElements(message, arguments);
		return (events) -> new Rejected<>(message, arguments);
	}

	static <T> Behavior<T> reject(Throwable exception) {
		Require.nonNull(exception);
		return (events) -> new Rejected<>(exception.getMessage(), exception);
	}

	Reaction<T> applyEvents(Seq<?> events);

	default Seq<?> changes() {
		return reaction().events();
	}

	default <X> Behavior<X> map(Function<? super T, Behavior<X>> nextBehavior) {
		Require.nonNull(nextBehavior);
		return (events) -> applyEvents(events).mapBehavior(nextBehavior);
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

	default Reaction<T> reaction() {
		return applyEvents(Seq.empty());
	}

	default String rejected(BiFunction<String, Object[], String> formatter) {
		return reaction().get(t -> null, formatter);
	}

	default T result() {
		return reaction().get(Function.identity(), Throwing.of(IllegalStateException::new).asBiFunction());
	}
}
