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

public interface Outcome<T> {

	static <T> Outcome<T> of(Executor executor, CompletionStage<T> stage) {
		Require.nonNullElements(executor, stage);
		return new Outcome<T>() {

			@Override
			public <X> Outcome<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
				return of(executor, fn.apply(executor, stage));
			}

			@Override
			public Outcome<T> withExecutor(Executor executor) {
				return of(executor, stage);
			}
		};
	}

	static <T> Outcome<T> ofBlocking(Executor executor, Future<T> future) {
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

	static <T> Outcome<T> ofCompleted(Executor executor, T value) {
		return of(executor, CompletableFuture.completedFuture(value));
	}

	static <T> Outcome<T> ofFailed(Executor executor, Throwable exception) {
		CompletableFuture<T> future = new CompletableFuture<>();
		future.completeExceptionally(exception);
		return of(executor, future);
	}

	default Outcome<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
		return apply((e, s) -> s.acceptEitherAsync(other, action, e));
	}

	<X> Outcome<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn);

	default <U> Outcome<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
		return apply((e, s) -> s.applyToEitherAsync(other, fn, e));
	}

	default Outcome<T> async(Executor executor) {
		return withExecutor(executor);
	}

	default Outcome<T> exceptionally(Function<Throwable, ? extends T> fn) {
		return apply((e, s) -> s.exceptionally(fn));
	}

	default <U> Outcome<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
		return apply((e, s) -> s.handleAsync(fn, e));
	}

	default Outcome<T> handleException(TFunction<? super Throwable, T> fn) {
		return handle((t, ex) -> ex != null ? fn.apply(ex) : t);
	}

	default <X> Outcome<X> handleSuccess(TFunction<? super T, X> fn) {
		return handle((t, ex) -> ex != null ? Throwing.unchecked(ex) : fn.apply(t));
	}

	default Outcome<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
		return apply((e, s) -> s.runAfterBothAsync(other, action, e));
	}

	default Outcome<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
		return apply((e, s) -> s.runAfterEitherAsync(other, action, e));
	}

	default Outcome<T> sync() {
		return withExecutor(Runnable::run);
	}

	default Outcome<Void> thenAccept(Consumer<? super T> action) {
		return apply((e, s) -> s.thenAcceptAsync(action, e));
	}

	default <U> Outcome<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
		return apply((e, s) -> s.thenAcceptBothAsync(other, action, e));
	}

	default <U> Outcome<U> thenApply(Function<? super T, ? extends U> fn) {
		return apply((e, s) -> s.thenApplyAsync(fn, e));
	}

	default <U, V> Outcome<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
		return apply((e, s) -> s.thenCombineAsync(other, fn, e));
	}

	default <U> Outcome<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
		return apply((e, s) -> s.thenComposeAsync(fn, e));
	}

	default Outcome<Void> thenRun(Runnable action) {
		return apply((e, s) -> s.thenRunAsync(action, e));
	}

	default CompletionStage<T> toCompletionStage() {
		CompletableFuture<T> future = new CompletableFuture<>();
		whenCompleteSuccess(future::complete);
		whenCompleteException(future::completeExceptionally);
		return future;
	}

	default Outcome<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
		return apply((e, s) -> s.whenCompleteAsync(action, e));
	}

	default Outcome<T> whenCompleteException(Consumer<? super Throwable> action) {
		return whenComplete((t, ex) -> action.accept(ex));
	}

	default Outcome<T> whenCompleteSuccess(Consumer<? super T> action) {
		return whenComplete((t, ex) -> action.accept(t));
	}

	Outcome<T> withExecutor(Executor executor);
}
