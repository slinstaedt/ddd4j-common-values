package org.ddd4j.infrastructure.scheduler;

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
import org.ddd4j.value.Throwing.TSupplier;

public interface CompletableResult<T> {

	static <T> CompletableResult<T> ofWaiting(Executor executor, Future<T> future) {
		Require.nonNullElements(executor, future);
		return new CompletableResult<T>() {

			private final CompletableFuture<T> waiting = new CompletableFuture<>();

			@Override
			public <X> CompletableResult<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
				executor.execute(this::checkFuture);
				return of(executor, fn.apply(executor, waiting));
			}

			void checkFuture() {
				try {
					T value = future.get(500, TimeUnit.MILLISECONDS);
					waiting.complete(value);
				} catch (ExecutionException e) {
					waiting.completeExceptionally(e.getCause());
				} catch (InterruptedException | TimeoutException e) {
					executor.execute(this::checkFuture);
				}
			}
		};
	}

	static <T> CompletableResult<T> ofLazy(Executor executor, TSupplier<T> supplier) {
		Require.nonNullElements(executor, supplier);
		return new CompletableResult<T>() {

			@Override
			public <X> CompletableResult<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
				CompletableFuture<T> supplied = CompletableFuture.supplyAsync(supplier, executor);
				return of(executor, fn.apply(executor, supplied));
			}
		};
	}

	static <T> CompletableResult<T> of(Executor executor, CompletionStage<T> stage) {
		Require.nonNullElements(executor, stage);
		return new CompletableResult<T>() {

			@Override
			public <X> CompletableResult<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
				return of(executor, fn.apply(executor, stage));
			}
		};
	}

	<X> CompletableResult<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn);

	default <U> CompletableResult<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
		return apply((e, s) -> s.handleAsync(fn, e));
	}

	default CompletableResult<T> handleException(TFunction<? super Throwable, T> fn) {
		return handle((t, e) -> e != null ? fn.apply(e) : t);
	}

	default <X> CompletableResult<X> handleSuccess(TFunction<? super T, X> fn) {
		return handle((t, e) -> e != null ? Throwing.unchecked(e) : fn.apply(t));
	}

	default CompletableResult<Void> thenAccept(Consumer<? super T> action) {
		return apply((e, s) -> s.thenAcceptAsync(action, e));
	}

	default <U> CompletableResult<U> thenApply(Function<? super T, ? extends U> fn) {
		return apply((e, s) -> s.thenApplyAsync(fn, e));
	}

	default CompletableResult<Void> thenRun(Runnable action) {
		return apply((e, s) -> s.thenRunAsync(action, e));
	}

	default CompletableResult<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
		return apply((e, s) -> s.whenCompleteAsync(action, e));
	}
}
