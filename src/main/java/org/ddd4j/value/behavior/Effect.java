package org.ddd4j.value.behavior;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.value.collection.Seq;

public interface Effect<T> {

	interface Interpreter {

		<T> CompletionStage<Reaction<T>> interpret(Effect<? extends T> effect);

		CompletionStage<Reaction<Void>> interpret(Object effect);
	}

	@FunctionalInterface
	interface Multiple extends Effect<Seq<?>> {

		Seq<CompletionStage<? extends Reaction<?>>> acceptMultiple(Interpreter interpreter);
	}

	@FunctionalInterface
	interface Side<T> extends Effect<T> {

		@Override
		CompletionStage<Reaction<T>> accept(Interpreter interpreter);
	}

	static Side<Void> NONE = of(Reaction.accepted(Seq.empty()));

	@SuppressWarnings("unchecked")
	static <T> CompletionStage<? extends Reaction<T>> cast(CompletionStage<? extends Reaction<? extends T>> stage) {
		return (CompletionStage<? extends Reaction<T>>) stage;
	}

	static <T> Side<T> failingWith(String message, Object... arguments) {
		return of(Reaction.rejected(message, arguments));
	}

	static <T> Side<T> just(T result) {
		return of(Reaction.accepted(result, Seq.empty()));
	}

	static <T> Side<T> of(Reaction<T> reaction) {
		Require.nonNull(reaction);
		return i -> CompletableFuture.completedFuture(reaction);
	}

	static Effect<?> ofAny(Object effect) {
		return effect instanceof Effect ? (Effect<?>) effect : ofCommand(effect);
	}

	static Side<Void> ofCommand(Object command) {
		Require.NOTHING.nonNull().not(Effect.class::isInstance).test(command);
		return i -> i.interpret(command);
	}

	default CompletionStage<Reaction<T>> accept(Interpreter interpreter) {
		return interpreter.interpret(this);
	}

	default <X> Side<X> chain(Effect<X> effect) {
		Require.nonNull(effect);
		return i -> {
			this.accept(i);
			return effect.accept(i);
		};
	}

	default Multiple chain(Seq<Effect<?>> effects) {
		return i -> effects.map().to(e -> e.accept(i));
	}

	default <U, V> Side<V> combine(Effect<? extends U> other,
			BiFunction<? super Reaction<? extends T>, ? super Reaction<? extends U>, ? extends Effect<V>> fn) {
		return i -> accept(i).thenCombine(other.accept(i), fn).thenCompose(e -> e.accept(i));
	}

	default <U, V> Side<V> combine(Effect<? extends U> other, BiFunction<? super T, ? super U, ? extends Effect<V>> accepted,
			BiFunction<String, Object[], ? extends Effect<V>> rejected) {
		return this.<U, V> combine(other, (rt, ru) -> rt.fold(t -> ru.fold(u -> accepted.apply(t, u), rejected), rejected));
	}

	default <U> Side<U> either(Effect<? extends T> other, Function<? super Reaction<? extends T>, ? extends Effect<U>> fn) {
		return i -> accept(i).applyToEither(cast(other.accept(i)), fn).thenCompose(e -> e.accept(i));
	}

	default <U> Side<U> either(Effect<? extends T> other, Function<? super T, ? extends Effect<U>> accepted,
			BiFunction<String, Object[], ? extends Effect<U>> rejected) {
		return either(other, r -> r.fold(t -> accepted.apply(t), rejected));
	}

	default Side<Void> on(Consumer<? super T> accepted, BiConsumer<String, Object[]> rejected) {
		Require.nonNullElements(accepted, rejected);
		return on(t -> {
			accepted.accept(t);
			return null;
		} , (msg, args) -> {
			rejected.accept(msg, args);
			return null;
		});
	}

	default <X> Side<X> on(Function<? super T, Effect<X>> accepted, BiFunction<String, Object[], ? extends Effect<X>> rejected) {
		Require.nonNullElements(accepted, rejected);
		return i -> accept(i).thenCompose(r -> r.fold(accepted, rejected).accept(i));
	}

	default <X> Side<X> onAccepted(Function<? super T, Effect<X>> accepted) {
		return on(accepted, Effect::failingWith);
	}

	default <X> Side<X> onRejected(Function<? super T, X> accepted, BiFunction<String, Object[], Effect<X>> rejected) {
		Require.nonNullElements(accepted, rejected);
		return on(t -> just(accepted.apply(t)), rejected);
	}
}
