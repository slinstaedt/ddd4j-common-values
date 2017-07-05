package org.ddd4j.infrastructure;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
import org.ddd4j.Throwing.Producer;
import org.ddd4j.Throwing.TBiConsumer;
import org.ddd4j.Throwing.TBiFunction;
import org.ddd4j.Throwing.TBiPredicate;
import org.ddd4j.Throwing.TConsumer;
import org.ddd4j.Throwing.TFunction;
import org.ddd4j.Throwing.TPredicate;

public interface Promise<T> {

	class Deferred<T> implements Promise<T> {

		private final Executor executor;
		private final CompletableFuture<T> future;

		public Deferred(Executor executor) {
			this(executor, new CompletableFuture<>());
		}

		Deferred(Executor executor, CompletableFuture<T> future) {
			this.executor = Require.nonNull(executor);
			this.future = Require.nonNull(future);
		}

		@Override
		public <X> Promise<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
			return of(executor, fn.apply(executor, future));
		}

		public boolean cancel(boolean mayInterruptIfRunning) {
			return future.cancel(mayInterruptIfRunning);
		}

		public void complete(T value, Throwable exception) {
			if (exception != null) {
				completeExceptionally(exception);
			} else {
				completeSuccessfully(value);
			}
		}

		public void completeExceptionally(Throwable exception) {
			future.completeExceptionally(exception);
		}

		public void completeSuccessfully(T value) {
			future.complete(value);
		}

		@Override
		public CompletionStage<T> toCompletionStage() {
			return future;
		}
	}

	class Ordered<T> implements Promise<T> {

		private volatile Promise<T> current;
		private T result;
		private Throwable exception;

		public Ordered(Promise<T> trigger) {
			current = Require.nonNull(trigger).whenComplete(this::setOutcome);
		}

		private void setOutcome(T result, Throwable exception) {
			this.result = result;
			this.exception = exception;
		}

		private T getOutcome() {
			return exception != null ? Throwing.unchecked(exception) : result;
		}

		@Override
		public synchronized <X> Promise<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
			Promise<X> promise = current.apply(fn);
			current = promise.handle((x, ex) -> getOutcome());
			return promise;
		}

		@Override
		public CompletionStage<T> toCompletionStage() {
			return current.toCompletionStage();
		}
	}

	static void main(String... args) throws InterruptedException {
		Random random = new Random();
		ExecutorService executor = Executors.newCachedThreadPool();

		Deferred<String> deferred = Promise.deferred(executor);
		Promise<String> ordered = deferred.ordered();
		ordered.whenComplete((s, x) -> System.out.println("start"));
		for (int i = 0; i < 10; i++) {
			int c = i;
			ordered.whenComplete((s, x) -> {
				Thread.sleep(random.nextInt(500));
				System.out.println(c + ": " + s);
			});
		}
		deferred.complete("XXX", new Exception());
		ordered.whenComplete((s, x) -> System.out.println("end"));
		deferred.whenComplete((s, x) -> System.out.println("starting..."));

		System.out.println("Executor shutdown");
	}

	static Promise<Void> completed() {
		return completed(null);
	};

	static <T> Promise<T> completed(T value) {
		CompletableFuture<T> future = new CompletableFuture<>();
		future.complete(value);
		return of(Runnable::run, future);
	}

	static <T> Deferred<T> deferred(Executor executor) {
		return new Deferred<>(executor);
	}

	static <T> Promise<T> failed(Throwable exception) {
		CompletableFuture<T> future = new CompletableFuture<>();
		future.completeExceptionally(exception);
		return of(Runnable::run, future);
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

	default T join() {
		return toCompletionStage().toCompletableFuture().join();
	}

	default Promise<T> ordered() {
		return new Ordered<>(this);
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
		return whenCompleteSuccessfully(predicate.throwOnFail(IllegalArgumentException::new));
	}

	default <U> Promise<T> testAndFail(Promise<U> other, TBiPredicate<? super T, ? super U> predicate) {
		return thenCombine(other, (t, u) -> {
			if (!predicate.test(t, u)) {
				throw new IllegalArgumentException("T: " + t + ", U:" + u);
			}
			return t;
		});
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
		return thenApply(t -> factory.get());
	}

	default <V> Promise<V> thenReturnValue(V value) {
		return thenApply(t -> value);
	}

	default Promise<T> thenRun(Runnable action) {
		return apply((e, s) -> s.thenApplyAsync(t -> {
			action.run();
			return t;
		}, e));
	}

	default CompletionStage<T> toCompletionStage() {
		CompletableFuture<T> future = new CompletableFuture<>();
		whenCompleteSuccessfully(future::complete);
		whenCompleteExceptionally(future::completeExceptionally);
		return future;
	}

	default Promise<T> whenComplete(TBiConsumer<? super T, ? super Throwable> action) {
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
