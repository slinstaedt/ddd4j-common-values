package org.ddd4j.value.behavior;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.value.collection.Seq;

@FunctionalInterface
public interface Effect<T> {

	interface Dispatcher {

		<T> CompletionStage<Reaction<T>> dispatch(Effect<? extends T> effect);

		CompletionStage<Reaction<Void>> dispatch(Object effect);
	}

	@FunctionalInterface
	interface Multiple extends Effect<Seq<?>> {

		Seq<CompletionStage<? extends Reaction<?>>> acceptMultiple(Dispatcher dispatcher);

		@Override
		default CompletionStage<Reaction<Seq<?>>> accept(org.ddd4j.value.behavior.Effect.Dispatcher dispatcher) {
			// TODO Auto-generated method stub
			return null;
		}
	}

	interface Query<T> extends Effect<T> {

		@Override
		default CompletionStage<Reaction<T>> accept(Dispatcher dispatcher) {
			return dispatcher.dispatch(this);
		}
	}

	static Effect<Void> NONE = of(Reaction.accepted(Seq.empty()));

	@SuppressWarnings("unchecked")
	static <T> CompletionStage<? extends Reaction<T>> cast(CompletionStage<? extends Reaction<? extends T>> stage) {
		return (CompletionStage<? extends Reaction<T>>) stage;
	}

	static <T> Effect<T> failingWith(String message, Object... arguments) {
		return of(Reaction.rejected(message, arguments));
	}

	static <T> Effect<T> just(T result) {
		return of(Reaction.accepted(result, Seq.empty()));
	}

	static <T> Effect<T> of(Reaction<T> reaction) {
		Require.nonNull(reaction);
		return d -> CompletableFuture.completedFuture(reaction);
	}

	static Effect<?> ofAny(Object effect) {
		return effect instanceof Effect ? (Effect<?>) effect : ofCommand(effect);
	}

	static Effect<Void> ofCommand(Object command) {
		Require.NOTHING.nonNull().not(Effect.class::isInstance).test(command);
		return d -> d.dispatch(command);
	}

	CompletionStage<Reaction<T>> accept(Dispatcher dispatcher);

	default <X> Effect<X> chain(Effect<X> effect) {
		Require.nonNull(effect);
		return d -> {
			this.accept(d);
			return effect.accept(d);
		};
	}

	default Multiple chain(Seq<Effect<?>> effects) {
		return d -> effects.map().to(e -> e.accept(d));
	}

	default <U, V> Effect<V> combine(Effect<? extends U> other,
			BiFunction<? super Reaction<? extends T>, ? super Reaction<? extends U>, ? extends Effect<V>> fn) {
		return d -> accept(d).thenCombine(other.accept(d), fn).thenCompose(e -> e.accept(d));
	}

	default <U, V> Effect<V> combine(Effect<? extends U> other, BiFunction<? super T, ? super U, ? extends Effect<V>> accepted,
			BiFunction<String, Object[], ? extends Effect<V>> rejected) {
		return this.<U, V>combine(other, (rt, ru) -> rt.fold(t -> ru.fold(u -> accepted.apply(t, u), rejected), rejected));
	}

	default <U> Effect<U> either(Effect<? extends T> other, Function<? super Reaction<? extends T>, ? extends Effect<U>> fn) {
		return d -> accept(d).applyToEither(cast(other.accept(d)), fn).thenCompose(e -> e.accept(d));
	}

	default <U> Effect<U> either(Effect<? extends T> other, Function<? super T, ? extends Effect<U>> accepted,
			BiFunction<String, Object[], ? extends Effect<U>> rejected) {
		return either(other, r -> r.fold(t -> accepted.apply(t), rejected));
	}

	default Effect<Void> on(Consumer<? super T> accepted, BiConsumer<String, Object[]> rejected) {
		Require.nonNullElements(accepted, rejected);
		return on(t -> {
			accepted.accept(t);
			return null;
		}, (msg, args) -> {
			rejected.accept(msg, args);
			return null;
		});
	}

	default <X> Effect<X> on(Function<? super T, Effect<X>> accepted, BiFunction<String, Object[], ? extends Effect<X>> rejected) {
		Require.nonNullElements(accepted, rejected);
		return d -> accept(d).thenCompose(r -> r.fold(accepted, rejected).accept(d));
	}

	default <X> Effect<X> onAccepted(Function<? super T, Effect<X>> accepted) {
		return on(accepted, Effect::failingWith);
	}

	default <X> Effect<X> onRejected(Function<? super T, X> accepted, BiFunction<String, Object[], Effect<X>> rejected) {
		Require.nonNullElements(accepted, rejected);
		return on(t -> just(accepted.apply(t)), rejected);
	}
}
