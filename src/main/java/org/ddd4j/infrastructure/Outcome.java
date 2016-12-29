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
import org.ddd4j.value.Throwing.TSupplier;

@FunctionalInterface
public interface Outcome<T> {

	@FunctionalInterface
	interface Stage<T> extends Outcome<T>, CompletionStage<T> {

		@Override
		default Stage<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
			return apply((e, s) -> s.acceptEitherAsync(other, action, e));
		}

		@Override
		default Stage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
			return apply((e, s) -> s.acceptEitherAsync(other, action, e));
		}

		@Override
		default Stage<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor) {
			return apply((e, s) -> s.acceptEitherAsync(other, action, executor));
		}

		@Override
		<X> Stage<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn);

		@Override
		default <U> Stage<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
			return apply((e, s) -> s.applyToEither(other, fn));
		}

		@Override
		default <U> Stage<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
			return apply((e, s) -> s.applyToEitherAsync(other, fn, e));
		}

		@Override
		default <U> Stage<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor) {
			return apply((e, s) -> s.applyToEitherAsync(other, fn, executor));
		}

		@Override
		default Stage<T> exceptionally(Function<Throwable, ? extends T> fn) {
			return apply((e, s) -> s.exceptionally(fn));
		}

		@Override
		default <U> Stage<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
			return apply((e, s) -> s.handle(fn));
		}

		@Override
		default <U> Stage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
			return apply((e, s) -> s.handleAsync(fn, e));
		}

		@Override
		default <U> Stage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
			return apply((e, s) -> s.handleAsync(fn, executor));
		}

		@Override
		default Stage<T> handleException(TFunction<? super Throwable, T> fn) {
			return handleAsync((t, e) -> e != null ? fn.apply(e) : t);
		}

		@Override
		default <X> Stage<X> handleSuccess(TFunction<? super T, X> fn) {
			return handleAsync((t, e) -> e != null ? Throwing.unchecked(e) : fn.apply(t));
		}

		@Override
		default Stage<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
			return apply((e, s) -> s.runAfterBoth(other, action));
		}

		@Override
		default Stage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
			return apply((e, s) -> s.runAfterBothAsync(other, action, e));
		}

		@Override
		default Stage<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
			return apply((e, s) -> s.runAfterBothAsync(other, action, executor));
		}

		@Override
		default Stage<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
			return apply((e, s) -> s.runAfterEither(other, action));
		}

		@Override
		default Stage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
			return apply((e, s) -> s.runAfterEitherAsync(other, action, e));
		}

		@Override
		default Stage<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
			return apply((e, s) -> s.runAfterEitherAsync(other, action, executor));
		}

		@Override
		default Stage<Void> thenAccept(Consumer<? super T> action) {
			return apply((e, s) -> s.thenAccept(action));
		}

		@Override
		default Stage<Void> thenAcceptAsync(Consumer<? super T> action) {
			return apply((e, s) -> s.thenAcceptAsync(action, e));
		}

		@Override
		default Stage<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
			return apply((e, s) -> s.thenAcceptAsync(action, executor));
		}

		@Override
		default <U> Stage<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
			return apply((e, s) -> s.thenAcceptBoth(other, action));
		}

		@Override
		default <U> Stage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
			return apply((e, s) -> s.thenAcceptBothAsync(other, action, e));
		}

		@Override
		default <U> Stage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor) {
			return apply((e, s) -> s.thenAcceptBothAsync(other, action, executor));
		}

		@Override
		default <U> Stage<U> thenApply(Function<? super T, ? extends U> fn) {
			return apply((e, s) -> s.thenApply(fn));
		}

		@Override
		default <U> Stage<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
			return apply((e, s) -> s.thenApplyAsync(fn, e));
		}

		@Override
		default <U> Stage<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
			return apply((e, s) -> s.thenApplyAsync(fn, executor));
		}

		@Override
		default <U, V> Stage<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
			return apply((e, s) -> s.thenCombine(other, fn));
		}

		@Override
		default <U, V> Stage<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
			return apply((e, s) -> s.thenCombineAsync(other, fn, e));
		}

		@Override
		default <U, V> Stage<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
			return apply((e, s) -> s.thenCombineAsync(other, fn, executor));
		}

		@Override
		default <U> Stage<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
			return apply((e, s) -> s.thenCompose(fn));
		}

		@Override
		default <U> Stage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
			return apply((e, s) -> s.thenComposeAsync(fn, e));
		}

		@Override
		default <U> Stage<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
			return apply((e, s) -> s.thenComposeAsync(fn, executor));
		}

		@Override
		default Stage<Void> thenRun(Runnable action) {
			return apply((e, s) -> s.thenRun(action));
		}

		@Override
		default Stage<Void> thenRunAsync(Runnable action) {
			return apply((e, s) -> s.thenRunAsync(action, e));
		}

		@Override
		default Stage<Void> thenRunAsync(Runnable action, Executor executor) {
			return apply((e, s) -> s.thenRunAsync(action, executor));
		}

		@Override
		default CompletableFuture<T> toCompletableFuture() {
			throw new UnsupportedOperationException();
		}

		@Override
		default Stage<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
			return apply((e, s) -> s.whenComplete(action));
		}

		@Override
		default Stage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
			return apply((e, s) -> s.whenCompleteAsync(action, e));
		}

		@Override
		default Stage<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
			return apply((e, s) -> s.whenCompleteAsync(action, executor));
		}
	}

	static <T> Outcome<T> of(Executor executor, CompletionStage<T> stage) {
		Require.nonNullElements(executor, stage);
		return new Outcome<T>() {

			@Override
			public <X> Outcome<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
				return of(executor, fn.apply(executor, stage));
			}
		};
	}

	static <T> Outcome<T> ofBlocking(Executor executor, Future<T> future) {
		Require.nonNullElements(executor, future);
		return new Outcome<T>() {

			private final CompletableFuture<T> result = new CompletableFuture<>();

			@Override
			public <X> Outcome<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
				executor.execute(this::checkFuture);
				return of(executor, fn.apply(executor, result));
			}

			void checkFuture() {
				try {
					T value = future.get(500, TimeUnit.MILLISECONDS);
					result.complete(value);
				} catch (ExecutionException e) {
					result.completeExceptionally(e.getCause());
				} catch (InterruptedException | TimeoutException e) {
					executor.execute(this::checkFuture);
				}
			}
		};
	}

	static <T> Outcome<T> ofEager(Executor executor, TSupplier<T> supplier) {
		CompletableFuture<T> supplied = CompletableFuture.supplyAsync(supplier, executor);
		return of(executor, supplied);
	}

	static <T> Outcome<T> ofLazy(Executor executor, TSupplier<T> supplier) {
		Require.nonNullElements(executor, supplier);
		return new Outcome<T>() {

			@Override
			public <X> Outcome<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
				CompletableFuture<T> supplied = CompletableFuture.supplyAsync(supplier, executor);
				return of(executor, fn.apply(executor, supplied));
			}
		};
	}

	static <T> Stage<T> ofStage(Executor executor, CompletionStage<T> stage) {
		Require.nonNullElements(executor, stage);
		return new Stage<T>() {

			@Override
			public <X> Stage<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
				return ofStage(executor, fn.apply(executor, stage));
			}
		};
	}

	static <T> Outcome<T> ofValue(Executor executor, T value) {
		return of(executor, CompletableFuture.completedFuture(value));
	}

	default Outcome<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
		return apply((e, s) -> s.acceptEitherAsync(other, action, e));
	}

	<X> Outcome<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn);

	default <U> Outcome<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
		return apply((e, s) -> s.applyToEitherAsync(other, fn, e));
	}

	default Outcome<T> exceptionally(Function<Throwable, ? extends T> fn) {
		return apply((e, s) -> s.exceptionally(fn));
	}

	default <U> Outcome<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
		return apply((e, s) -> s.handleAsync(fn, e));
	}

	default Outcome<T> handleException(TFunction<? super Throwable, T> fn) {
		return handle((t, e) -> e != null ? fn.apply(e) : t);
	}

	default <X> Outcome<X> handleSuccess(TFunction<? super T, X> fn) {
		return handle((t, e) -> e != null ? Throwing.unchecked(e) : fn.apply(t));
	}

	default Outcome<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
		return apply((e, s) -> s.runAfterBothAsync(other, action, e));
	}

	default Outcome<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
		return apply((e, s) -> s.runAfterEitherAsync(other, action, e));
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

	default Outcome<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
		return apply((e, s) -> s.whenCompleteAsync(action, e));
	}
}
