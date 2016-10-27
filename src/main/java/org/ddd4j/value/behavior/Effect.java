package org.ddd4j.value.behavior;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Either;
import org.ddd4j.value.Nothing;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.collection.Tpl;

@FunctionalInterface
public interface Effect<T> {

	interface Dispatcher {

		CompletionStage<Reaction<Void>> dispatch(Object command);

		<T> CompletionStage<Reaction<T>> dispatch(Query<? extends T> query);
	}

	@FunctionalInterface
	interface Multiple<T> {

		Seq<CompletionStage<? extends Reaction<? extends T>>> accept(Dispatcher dispatcher);

		default <X> Effect<X> composeAll(Function<Seq<Reaction<? extends T>>, Effect<X>> fn) {
			return d -> {
				Seq<CompletableFuture<? extends Reaction<? extends T>>> futures = accept(d).map().to(CompletionStage::toCompletableFuture);
				return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
						.thenApply(v -> fn.apply(futures.map().to(CompletableFuture::join)).accept(d).toCompletableFuture().join());
			};
		}

		@SuppressWarnings("unchecked")
		default <X> Effect<X> composeAnyAccepted(Function<? super T, Effect<X>> accepted) {
			return d -> CompletableFuture
					.anyOf(accept(d).map()
							.to(c -> c.thenApply(Reaction::result))
							.map()
							.to(CompletionStage::toCompletableFuture)
							.toArray(CompletableFuture[]::new))
					.thenApply(t -> accepted.apply((T) t))
					.thenCompose(e -> e.accept(d));
		}
	}

	interface Query<T> extends Effect<T> {

		@Override
		default CompletionStage<Reaction<T>> accept(Dispatcher dispatcher) {
			return dispatcher.dispatch(this);
		}
	}

	static Effect<Nothing> NONE = of(Reaction.accepted(Nothing.INSTANCE, Seq.empty()));

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

	default <X> Multiple<X> chain(Seq<? extends Effect<? extends X>> effects) {
		return d -> {
			this.accept(d);
			return effects.map().to(e -> e.accept(d));
		};
	}

	default <U> Effect<U> either(Effect<? extends T> other, Function<? super Reaction<? extends T>, ? extends Effect<U>> fn) {
		return d -> accept(d).applyToEither(cast(other.accept(d)), fn).thenCompose(e -> e.accept(d));
	}

	default <U> Effect<U> either(Effect<? extends T> other, Function<? super T, ? extends Effect<U>> accepted,
			BiFunction<String, Object[], ? extends Effect<U>> rejected) {
		return either(other, r -> r.foldReaction(accepted, rejected));
	}

	default <X> Effect<X> flatMap(Function<? super T, Effect<X>> accepted, BiFunction<String, Object[], ? extends Effect<X>> rejected) {
		Require.nonNullElements(accepted, rejected);
		return d -> accept(d).thenCompose(r -> r.foldReaction(accepted, rejected).accept(d));
	}

	default <X> Effect<X> flatMapAccepted(Function<? super T, Effect<X>> accepted) {
		return flatMap(accepted, Effect::failingWith);
	}

	default <X> Effect<X> flatMapRejected(Function<? super T, X> accepted, BiFunction<String, Object[], Effect<X>> rejected) {
		Require.nonNullElements(accepted, rejected);
		return flatMap(t -> just(accepted.apply(t)), rejected);
	}

	default <U, V> Effect<V> join(Effect<? extends U> other, BiFunction<? super Reaction<? extends T>, ? super Reaction<? extends U>, ? extends Effect<V>> fn) {
		return d -> accept(d).thenCombine(other.accept(d), fn).thenCompose(e -> e.accept(d));
	}

	default <U, V> Effect<V> join(Effect<? extends U> other, BiFunction<? super T, ? super U, ? extends Effect<V>> accepted,
			BiFunction<String, Object[], ? extends Effect<V>> rejected) {
		return this.<U, V>join(other, (rt, ru) -> rt.foldReaction(t -> ru.foldReaction(u -> accepted.apply(t, u), rejected), rejected));
	}

	default <X> Effect<Tpl<T, X>> joinBoth(Effect<X> effect) {
		Require.nonNull(effect);
		return d -> accept(d).thenCombine(effect.accept(d), Reaction::<X>and);
	}

	default <X> Effect<Either.OrBoth<T, X>> joinEither(Effect<X> effect) {
		Require.nonNull(effect);
		return d -> accept(d).thenCombine(effect.accept(d), Reaction::<X>or);
	}

	default <X> Effect<X> mapAccepted(Function<? super T, ? extends X> mapper) {
		Require.nonNullElements(mapper);
		return d -> accept(d).thenApply(r -> r.mapResult(mapper));
	}

	default Effect<Nothing> on(Consumer<? super T> accepted, BiConsumer<String, Object[]> rejected) {
		Require.nonNullElements(accepted, rejected);
		return flatMap(t -> {
			accepted.accept(t);
			return NONE;
		}, (msg, args) -> {
			rejected.accept(msg, args);
			return NONE;
		});
	}
}
