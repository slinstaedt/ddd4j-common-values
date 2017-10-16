package org.ddd4j.infrastructure;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
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
import org.ddd4j.Throwing.Task;

public interface Promise<T> {

	class Base<T, S extends CompletionStage<T>> implements Promise<T> {

		protected final Executor executor;
		protected final S stage;

		public Base(Executor executor, S stage) {
			this.executor = Require.nonNull(executor);
			this.stage = Require.nonNull(stage);
		}

		@Override
		public <X> Promise<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
			return Promise.of(executor, fn.apply(executor, stage));
		}

		@Override
		public CompletionStage<T> toCompletionStage() {
			return stage;
		}
	}

	interface Cancelable<T> extends Promise<T> {

		boolean cancel();

		boolean isDone();
	}

	class Deferred<T> extends Base<T, CompletableFuture<T>> implements Cancelable<T> {

		public Deferred(Executor executor) {
			super(executor, new CompletableFuture<>());
		}

		public Deferred(Executor executor, CompletableFuture<T> future) {
			super(executor, future);
		}

		@Override
		public boolean cancel() {
			return stage.cancel(false);
		}

		public boolean complete(T value, Throwable exception) {
			return exception == null ? completeSuccessfully(value) : completeExceptionally(exception);
		}

		public boolean completeExceptionally(Throwable exception) {
			return stage.completeExceptionally(exception);
		}

		public boolean completeSuccessfully(T value) {
			return stage.complete(value);
		}

		public boolean completeWith(Producer<? extends T> producer) {
			try {
				T value = producer.call();
				return completeSuccessfully(value);
			} catch (Throwable e) {
				return completeExceptionally(e);
			}
		}

		@Override
		public boolean isDone() {
			return stage.isDone();
		}
	}

	class Delayed<T> extends Base<T, CompletableFuture<T>> implements Cancelable<T>, java.util.concurrent.Delayed {

		private final java.util.concurrent.Future<T> future;
		private final java.util.concurrent.Delayed delayed;

		public Delayed(Executor executor, ScheduledFuture<T> future) {
			this(executor, future, future);
		}

		public Delayed(Executor executor, java.util.concurrent.Future<T> future, java.util.concurrent.Delayed delayed) {
			super(executor, new CompletableFuture<>());
			this.future = Require.nonNull(future);
			this.delayed = Require.nonNull(delayed);
		}

		@Override
		public boolean cancel() {
			return future.cancel(false);
		}

		@Override
		public int compareTo(java.util.concurrent.Delayed o) {
			return delayed.compareTo(o);
		}

		@Override
		public long getDelay(TimeUnit unit) {
			return delayed.getDelay(unit);
		}

		public void ifOverdueOrElse(Consumer<Delayed<T>> overdue, Consumer<Delayed<T>> orElse) {
			if (isOverdue()) {
				overdue.accept(this);
			} else {
				orElse.accept(this);
			}
		}

		@Override
		public boolean isDone() {
			return future.isDone();
		}

		public boolean isOverdue() {
			return getDelay(TimeUnit.MILLISECONDS) <= 0;
		}
	}

	class Future<T> extends Base<T, CompletableFuture<T>> implements Cancelable<T> {

		private final java.util.concurrent.Future<T> future;

		public Future(Executor executor, java.util.concurrent.Future<T> future, CompletableFuture<T> stage) {
			super(executor, stage);
			this.future = Require.nonNull(future);
		}

		@Override
		public boolean cancel() {
			stage.cancel(false);
			return future.cancel(false);
		}

		@Override
		public boolean isDone() {
			return future.isDone();
		}

		void poll() {
			try {
				T value = future.get(500, TimeUnit.MILLISECONDS);
				stage.complete(value);
			} catch (ExecutionException e) {
				stage.completeExceptionally(e.getCause());
			} catch (InterruptedException | TimeoutException e) {
				executor.execute(this::poll);
			}
		}
	}

	class Ordered<T> implements Promise<T> {

		private volatile Promise<T> current;
		private T result;
		private Throwable exception;

		public Ordered(Promise<T> trigger) {
			current = Require.nonNull(trigger).whenComplete(this::setOutcome);
		}

		@Override
		public synchronized <X> Promise<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
			Promise<X> promise = current.apply(fn);
			current = promise.handle((x, ex) -> getOutcome());
			return promise;
		}

		private T getOutcome() {
			return exception != null ? Throwing.unchecked(exception) : result;
		}

		private void setOutcome(T result, Throwable exception) {
			this.result = result;
			this.exception = exception;
		}

		@Override
		public CompletionStage<T> toCompletionStage() {
			return current.toCompletionStage();
		}
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

	static void main(String... args) throws InterruptedException {
		Random random = new Random();
		ExecutorService executor = ForkJoinPool.commonPool();

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
		executor.awaitTermination(20, TimeUnit.SECONDS);
	}

	static <T> Promise<T> of(Executor executor, CompletionStage<T> stage) {
		return new Base<>(executor, stage);
	}

	@SuppressWarnings("unchecked")
	static <T> Promise.Cancelable<T> ofFuture(Executor executor, java.util.concurrent.Future<T> future) {
		Require.nonNullElements(executor, future);
		if (future instanceof Promise.Cancelable) {
			return ((Promise.Cancelable<T>) future);
		} else if (future instanceof CompletableFuture) {
			return new Future<>(executor, future, (CompletableFuture<T>) future);
		} else {
			Future<T> promise = new Future<>(executor, future, new CompletableFuture<>());
			executor.execute(promise::poll);
			return promise;
		}
	}

	@SuppressWarnings("unchecked")
	static <T> Promise.Delayed<T> ofDelayed(Executor executor, ScheduledFuture<T> future) {
		Require.nonNullElements(executor, future);
		if (future instanceof Promise.Delayed) {
			return (Promise.Delayed<T>) future;
		} else {
			return new Delayed<>(executor, future);
		}
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

	default Promise<T> checkOrFail(TPredicate<? super T> predicate) {
		return whenCompleteSuccessfully(predicate.throwOnFail(IllegalArgumentException::new));
	}

	default <U> Promise<Void> checkOrFailOnBoth(Promise<? extends U> other, TBiPredicate<? super T, ? super U> predicate) {
		return thenAcceptBoth(other, predicate.throwOnFail(IllegalArgumentException::new));
	}

	default Promise<T> exceptionally(TFunction<Throwable, ? extends T> fn) {
		return apply((e, s) -> s.exceptionally(fn));
	}

	default <U> Promise<U> handle(TBiFunction<? super T, Throwable, ? extends U> fn) {
		return apply((e, s) -> s.handleAsync(fn, e));
	}

	default <U> Promise<U> handle(TFunction<? super T, ? extends U> success, TFunction<? super Throwable, ? extends U> exception) {
		return handle((t, ex) -> {
			if (ex == null) {
				return success.apply(t);
			} else {
				return exception.apply(ex);
			}
		});
	}

	default T join() {
		return toCompletionStage().toCompletableFuture().join();
	}

	default Promise<T> on(T value, UnaryOperator<Promise<T>> then) {
		return on(t -> Objects.equals(t, value), then, Function.identity());
	}

	default <X> Promise<X> on(T value, Function<Promise<T>, Promise<X>> then, Function<Promise<T>, Promise<X>> orElse) {
		return on(t -> Objects.equals(t, value), then, orElse);
	}

	default Promise<T> on(TPredicate<? super T> condition, UnaryOperator<Promise<T>> then) {
		return on(condition, then, Function.identity());
	}

	default <X> Promise<X> on(TPredicate<? super T> condition, Function<Promise<T>, Promise<X>> then,
			Function<Promise<T>, Promise<X>> orElse) {
		return thenCompose(t -> condition.test(t) ? then.apply(this) : orElse.apply(this));
	}

	default Promise<T> onVisit(T value, Consumer<Promise<T>> then) {
		return on(t -> Objects.equals(t, value), p -> {
			then.accept(p);
			return p;
		}, Function.identity());
	}

	default Promise<T> onVisit(TPredicate<? super T> condition, Consumer<Promise<T>> then) {
		return on(condition, p -> {
			then.accept(p);
			return p;
		}, Function.identity());
	}

	default Promise<T> ordered() {
		return new Ordered<>(this);
	}

	default <X> X returning(X value) {
		return value;
	}

	default Promise<?> runAfterAll(Stream<Promise<?>> others) {
		return others.reduce(this, Promise::runAfterBoth);
	}

	default Promise<?> runAfterAny(Stream<Promise<?>> others) {
		return others.reduce(this, Promise::runAfterEither);
	}

	default Promise<Void> runAfterBoth(Promise<?> other) {
		return runAfterBoth(other, this::getClass);
	}

	default Promise<Void> runAfterBoth(Promise<?> other, Runnable action) {
		return apply((e, s) -> s.runAfterBothAsync(other.toCompletionStage(), action, e));
	}

	default Promise<Void> runAfterEither(Promise<?> other) {
		return runAfterEither(other, this::getClass);
	}

	default Promise<Void> runAfterEither(Promise<?> other, Runnable action) {
		return apply((e, s) -> s.runAfterEitherAsync(other.toCompletionStage(), action, e));
	}

	default Promise<T> sync() {
		return withExecutor(Runnable::run);
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

	default Promise<T> whenComplete(Task task) {
		return whenComplete((t, ex) -> task.run());
	}

	default Promise<T> whenComplete(TBiConsumer<? super T, ? super Throwable> action) {
		return apply((e, s) -> s.whenCompleteAsync(action, e));
	}

	default Promise<T> whenComplete(TConsumer<? super T> success, TConsumer<? super Throwable> exception) {
		return whenComplete((t, ex) -> {
			if (ex != null) {
				exception.acceptNonNull(ex);
			} else {
				success.acceptNonNull(t);
			}
		});
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
