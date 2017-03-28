package org.ddd4j.messaging;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.value.Either;
import org.ddd4j.value.Nothing;
import org.ddd4j.value.behavior.Reaction;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.collection.Tpl;

@FunctionalInterface
public interface EffectDispatcher<T> {

	interface Dispatcher {

		CompletionStage<Reaction<Nothing>> dispatch(Object command);

		<T> CompletionStage<Reaction<T>> dispatch(Query<? extends T> query);
	}

	@FunctionalInterface
	interface Multiple<T> {

		Seq<CompletionStage<? extends Reaction<? extends T>>> accept(Dispatcher dispatcher);

		default <X> EffectDispatcher<X> composeAll(Function<Seq<Reaction<? extends T>>, EffectDispatcher<X>> fn) {
			return d -> {
				Seq<CompletableFuture<? extends Reaction<? extends T>>> futures = accept(d).map().to(CompletionStage::toCompletableFuture);
				return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
						.thenApply(v -> fn.apply(futures.map().to(CompletableFuture::join)).accept(d).toCompletableFuture().join());
			};
		}

		@SuppressWarnings("unchecked")
		default <X> EffectDispatcher<X> composeAnyAccepted(Function<? super T, EffectDispatcher<X>> accepted) {
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

	interface Query<T> extends EffectDispatcher<T> {

		@Override
		default CompletionStage<Reaction<T>> accept(Dispatcher dispatcher) {
			return dispatcher.dispatch(this);
		}
	}

	static EffectDispatcher<Nothing> NONE = of(Reaction.accepted(Nothing.INSTANCE, Seq.empty()));

	@SuppressWarnings("unchecked")
	static <T> CompletionStage<? extends Reaction<T>> cast(CompletionStage<? extends Reaction<? extends T>> stage) {
		return (CompletionStage<? extends Reaction<T>>) stage;
	}

	static <T> EffectDispatcher<T> failingWith(String message, Object... arguments) {
		return of(Reaction.rejected(message, arguments));
	}

	static <T> EffectDispatcher<T> just(T result) {
		return of(Reaction.accepted(result, Seq.empty()));
	}

	static <T> EffectDispatcher<T> of(Reaction<T> reaction) {
		Require.nonNull(reaction);
		return d -> CompletableFuture.completedFuture(reaction);
	}

	static EffectDispatcher<?> ofAny(Object effect) {
		return effect instanceof EffectDispatcher ? (EffectDispatcher<?>) effect : ofCommand(effect);
	}

	static EffectDispatcher<Nothing> ofCommand(Object command) {
		Require.NOTHING.nonNull().thatNot(EffectDispatcher.class::isInstance).test(command);
		return d -> d.dispatch(command);
	}

	CompletionStage<Reaction<T>> accept(Dispatcher dispatcher);

	default <X> EffectDispatcher<X> chain(EffectDispatcher<X> effect) {
		Require.nonNull(effect);
		return d -> {
			this.accept(d);
			return effect.accept(d);
		};
	}

	default <X> Multiple<X> chain(Seq<? extends EffectDispatcher<? extends X>> effects) {
		return d -> {
			this.accept(d);
			return effects.map().to(e -> e.accept(d));
		};
	}

	default <U> EffectDispatcher<U> either(EffectDispatcher<? extends T> other, Function<? super Reaction<? extends T>, ? extends EffectDispatcher<U>> fn) {
		return d -> accept(d).applyToEither(cast(other.accept(d)), fn).thenCompose(e -> e.accept(d));
	}

	default <U> EffectDispatcher<U> either(EffectDispatcher<? extends T> other, Function<? super T, ? extends EffectDispatcher<U>> accepted,
			BiFunction<String, Object[], ? extends EffectDispatcher<U>> rejected) {
		return either(other, r -> r.foldReaction(accepted, rejected));
	}

	default <X> EffectDispatcher<X> flatMap(Function<? super T, EffectDispatcher<X>> accepted,
			BiFunction<String, Object[], ? extends EffectDispatcher<X>> rejected) {
		Require.nonNullElements(accepted, rejected);
		return d -> accept(d).thenCompose(r -> r.foldReaction(accepted, rejected).accept(d));
	}

	default <X> EffectDispatcher<X> flatMapAccepted(Function<? super T, EffectDispatcher<X>> accepted) {
		return flatMap(accepted, EffectDispatcher::failingWith);
	}

	default <X> EffectDispatcher<X> flatMapRejected(Function<? super T, X> accepted, BiFunction<String, Object[], EffectDispatcher<X>> rejected) {
		Require.nonNullElements(accepted, rejected);
		return flatMap(t -> just(accepted.apply(t)), rejected);
	}

	default <U, V> EffectDispatcher<V> join(EffectDispatcher<? extends U> other,
			BiFunction<? super Reaction<? extends T>, ? super Reaction<? extends U>, ? extends EffectDispatcher<V>> fn) {
		return d -> accept(d).thenCombine(other.accept(d), fn).thenCompose(e -> e.accept(d));
	}

	default <U, V> EffectDispatcher<V> join(EffectDispatcher<? extends U> other, BiFunction<? super T, ? super U, ? extends EffectDispatcher<V>> accepted,
			BiFunction<String, Object[], ? extends EffectDispatcher<V>> rejected) {
		return this.<U, V>join(other, (rt, ru) -> rt.foldReaction(t -> ru.foldReaction(u -> accepted.apply(t, u), rejected), rejected));
	}

	default <X> EffectDispatcher<Tpl<T, X>> joinBoth(EffectDispatcher<X> effect) {
		Require.nonNull(effect);
		return d -> accept(d).thenCombine(effect.accept(d), Reaction::<X>and);
	}

	default <X> EffectDispatcher<Either.OrBoth<T, X>> joinEither(EffectDispatcher<X> effect) {
		Require.nonNull(effect);
		return d -> accept(d).thenCombine(effect.accept(d), Reaction::<X>or);
	}

	default <X> EffectDispatcher<X> mapAccepted(Function<? super T, ? extends X> mapper) {
		Require.nonNullElements(mapper);
		return d -> accept(d).thenApply(r -> r.mapResult(mapper));
	}

	default EffectDispatcher<Nothing> on(Consumer<? super T> accepted, BiConsumer<String, Object[]> rejected) {
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
