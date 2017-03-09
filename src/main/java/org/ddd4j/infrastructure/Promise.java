package org.ddd4j.infrastructure;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.ddd4j.Throwing;
import org.ddd4j.Throwing.Producer;
import org.ddd4j.Throwing.TBiFunction;
import org.ddd4j.Throwing.TConsumer;
import org.ddd4j.Throwing.TFunction;
import org.ddd4j.Throwing.TPredicate;
import org.ddd4j.contract.Require;

public interface Promise<T> {

	// FIXME memory leak?
	Promise<Void> COMPLETED = completed(null);

	static <T> Promise<T> completed(T value) {
		CompletableFuture<T> future = new CompletableFuture<>();
		future.complete(value);
		return of(Runnable::run, future);
	}

	static <T> Promise<T> failed(Throwable exception) {
		CompletableFuture<T> future = new CompletableFuture<>();
		future.completeExceptionally(exception);
		return of(Runnable::run, future);
	}

	static <T> Promise<T> never() {
		return of(Runnable::run, new CompletableFuture<>());
	}

	static <T> Promise<T> of(Executor executor, CompletionStage<T> stage) {
		Require.nonNullElements(executor, stage);
		return new Promise<T>() {

			@Override
			public <X> Promise<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
				return of(executor, fn.apply(executor, stage));
			}

			@Override
			public CompletionStage<T> toCompletionStage() {
				return stage;
			}
		};
	}

	static <T> Promise<T> ofBlocking(Executor executor, Future<T> future) {
		Require.nonNullElements(executor, future);
		CompletableFuture<T> result = new CompletableFuture<>();
		executor.execute(new Runnable() {

			@Override
			public void run() {
				try {
					T value = future.get(500, TimeUnit.MILLISECONDS);
					result.complete(value);
				} catch (ExecutionException e) {
					result.completeExceptionally(e.getCause());
				} catch (InterruptedException | TimeoutException e) {
					executor.execute(this);
				}
			}
		});
		return of(executor, result);
	}

	default Promise<Void> acceptEither(Promise<? extends T> other, TConsumer<? super T> action) {
		return apply((e, s) -> s.acceptEitherAsync(other.toCompletionStage(), action, e));
	}

	<X> Promise<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn);

	default <U> Promise<U> applyToEither(Promise<? extends T> other, TFunction<? super T, U> fn) {
		return apply((e, s) -> s.applyToEitherAsync(other.toCompletionStage(), fn, e));
	}

	default Promise<T> async(Executor executor) {
		return withExecutor(executor);
	}

	default Promise<T> exceptionally(TFunction<Throwable, ? extends T> fn) {
		return apply((e, s) -> s.exceptionally(fn));
	}

	default <U> Promise<U> handle(TBiFunction<? super T, Throwable, ? extends U> fn) {
		return apply((e, s) -> s.handleAsync(fn, e));
	}

	default Promise<T> handleException(TFunction<? super Throwable, T> fn) {
		return handle((t, ex) -> ex != null ? fn.apply(ex) : t);
	}

	default <X> Promise<X> handleSuccess(TFunction<? super T, X> fn) {
		return handle((t, ex) -> t != null ? fn.apply(t) : Throwing.unchecked(ex));
	}

	default T join() {
		return toCompletionStage().toCompletableFuture().join();
	}

	default Promise<?> runAfterAll(Stream<Promise<?>> others) {
		return others.reduce(this, Promise::runAfterBoth);
	}

	default Promise<?> runAfterAny(Stream<Promise<?>> others) {
		return others.reduce(this, Promise::runAfterEither);
	}

	default Promise<Void> runAfterBoth(Promise<?> other) {
		return runAfterBoth(other, this::hashCode);
	}

	default Promise<Void> runAfterBoth(Promise<?> other, Runnable action) {
		return apply((e, s) -> s.runAfterBothAsync(other.toCompletionStage(), action, e));
	}

	default Promise<Void> runAfterEither(Promise<?> other) {
		return runAfterEither(other, this::hashCode);
	}

	default Promise<Void> runAfterEither(Promise<?> other, Runnable action) {
		return apply((e, s) -> s.runAfterEitherAsync(other.toCompletionStage(), action, e));
	}

	default Promise<T> sync() {
		return withExecutor(Runnable::run);
	}

	default Promise<T> testAndFail(TPredicate<? super T> predicate) {
		return whenCompleteSuccessfully(predicate.throwOnFail(RuntimeException::new));
	}

	default Promise<Void> thenAccept(TConsumer<? super T> action) {
		return apply((e, s) -> s.thenAcceptAsync(action, e));
	}

	default <U> Promise<Void> thenAcceptBoth(Promise<? extends U> other, BiConsumer<? super T, ? super U> action) {
		return apply((e, s) -> s.thenAcceptBothAsync(other.toCompletionStage(), action, e));
	}

	default <U> Promise<U> thenApply(TFunction<? super T, ? extends U> fn) {
		return apply((e, s) -> s.thenApplyAsync(fn, e));
	}

	default <U, V> Promise<V> thenCombine(Promise<? extends U> other, TBiFunction<? super T, ? super U, ? extends V> fn) {
		return apply((e, s) -> s.thenCombineAsync(other.toCompletionStage(), fn, e));
	}

	default <U> Promise<U> thenCompose(TFunction<? super T, ? extends Promise<U>> fn) {
		return apply((e, s) -> s.thenComposeAsync(fn.andThen(Promise::toCompletionStage), e));
	}

	default <V> Promise<V> thenReturn(Producer<V> factory) {
		return handleSuccess(t -> factory.get());
	}

	default <V> Promise<V> thenReturnValue(V value) {
		return handleSuccess(t -> value);
	}

	default Promise<Void> thenRun(Runnable action) {
		return apply((e, s) -> s.thenRunAsync(action, e));
	}

	default CompletionStage<T> toCompletionStage() {
		CompletableFuture<T> future = new CompletableFuture<>();
		whenCompleteSuccessfully(future::complete);
		whenCompleteExceptionally(future::completeExceptionally);
		return future;
	}

	default Promise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
		return apply((e, s) -> s.whenCompleteAsync(action, e));
	}

	default Promise<T> whenCompleteExceptionally(TConsumer<? super Throwable> action) {
		return whenComplete((t, ex) -> action.acceptNonNull(ex));
	}

	default Promise<T> whenCompleteSuccessfully(TConsumer<? super T> action) {
		return whenComplete((t, ex) -> action.acceptNonNull(t));
	}

	default Promise<T> withExecutor(Executor executor) {
		return of(executor, toCompletionStage());
	}
}
