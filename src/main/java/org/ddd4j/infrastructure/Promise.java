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
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.Throwing.TFunction;

public interface Promise<T> {

	static <T> Promise<T> of(Executor executor, CompletionStage<T> stage) {
		Require.nonNullElements(executor, stage);
		return new Promise<T>() {

			@Override
			public <X> Promise<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
				return of(executor, fn.apply(executor, stage));
			}

			@Override
			public Promise<T> withExecutor(Executor executor) {
				return of(executor, stage);
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

	static <T> Promise<T> ofCompleted(Executor executor, T value) {
		return of(executor, CompletableFuture.completedFuture(value));
	}

	static <T> Promise<T> ofFailed(Executor executor, Throwable exception) {
		CompletableFuture<T> future = new CompletableFuture<>();
		future.completeExceptionally(exception);
		return of(executor, future);
	}

	default Promise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
		return apply((e, s) -> s.acceptEitherAsync(other, action, e));
	}

	<X> Promise<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn);

	default <U> Promise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
		return apply((e, s) -> s.applyToEitherAsync(other, fn, e));
	}

	default Promise<T> async(Executor executor) {
		return withExecutor(executor);
	}

	default Promise<T> exceptionally(Function<Throwable, ? extends T> fn) {
		return apply((e, s) -> s.exceptionally(fn));
	}

	default <U> Promise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
		return apply((e, s) -> s.handleAsync(fn, e));
	}

	default Promise<T> handleException(TFunction<? super Throwable, T> fn) {
		return handle((t, ex) -> ex != null ? fn.apply(ex) : t);
	}

	default <X> Promise<X> handleSuccess(TFunction<? super T, X> fn) {
		return handle((t, ex) -> ex != null ? Throwing.unchecked(ex) : fn.apply(t));
	}

	default Promise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
		return apply((e, s) -> s.runAfterBothAsync(other, action, e));
	}

	default Promise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
		return apply((e, s) -> s.runAfterEitherAsync(other, action, e));
	}

	default Promise<T> sync() {
		return withExecutor(Runnable::run);
	}

	default Promise<Void> thenAccept(Consumer<? super T> action) {
		return apply((e, s) -> s.thenAcceptAsync(action, e));
	}

	default <U> Promise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
		return apply((e, s) -> s.thenAcceptBothAsync(other, action, e));
	}

	default <U> Promise<U> thenApply(Function<? super T, ? extends U> fn) {
		return apply((e, s) -> s.thenApplyAsync(fn, e));
	}

	default <U, V> Promise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
		return apply((e, s) -> s.thenCombineAsync(other, fn, e));
	}

	default <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
		return apply((e, s) -> s.thenComposeAsync(fn, e));
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

	default Promise<T> whenCompleteExceptionally(Consumer<? super Throwable> action) {
		return whenComplete((t, ex) -> action.accept(ex));
	}

	default Promise<T> whenCompleteSuccessfully(Consumer<? super T> action) {
		return whenComplete((t, ex) -> action.accept(t));
	}

	Promise<T> withExecutor(Executor executor);
}