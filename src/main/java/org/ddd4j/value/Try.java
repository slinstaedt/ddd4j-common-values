package org.ddd4j.value;

import static java.util.Objects.requireNonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

@FunctionalInterface
public interface Try<T> extends Callable<T> {

	public static void main(String[] args) throws Exception {
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
			Try<Integer> dividend = Try.ofCallable(() -> Integer.valueOf(mainRead(reader, "x=")));
			Try<Integer> divisor = Try.ofCallable(() -> Integer.valueOf(mainRead(reader, "y=")));
			Try<Integer> result = dividend.<Integer>flatMapSuccess(x -> divisor.mapSuccess(y -> x / y));
			result = result.retry(1, executor::submit);
			System.out.println("Result of x/y=" + result.call());
		} finally {
			executor.shutdown();
		}
	}

	public static String mainRead(BufferedReader reader, String out) throws IOException {
		Thread thread = Thread.currentThread();
		System.out.println(thread);
		for (StackTraceElement element : thread.getStackTrace()) {
			if (element.getLineNumber() >= 0 && !element.getClassName().equals(Thread.class.getName()) && !element.getMethodName().equals("mainRead")) {
				System.out.println("\tat " + element);
			}
		}
		System.out.print(out);
		return reader.readLine();
	}

	static <T> Try<T> ofCallable(Callable<T> callable) {
		return requireNonNull(callable)::call;
	}

	static <T> Try<T> ofFailure(Throwable throwable) {
		requireNonNull(throwable);
		return () -> Throwing.unchecked(throwable);
	}

	static <T> Try<Void> ofRunnable(Runnable runnable) {
		requireNonNull(runnable);
		return () -> {
			runnable.run();
			return null;
		};
	}

	static <T> Try<T> ofSuccess(T result) {
		return () -> result;
	};

	static <T> Try<T> ofSupplier(Supplier<T> supplier) {
		return requireNonNull(supplier)::get;
	}

	default Callable<T> asCallable() {
		return this::invokeUnchecked;
	}

	default <X> Function<X, T> asFunction() {
		return x -> invokeUnchecked();
	}

	default Runnable asRunnable() {
		return this::invokeUnchecked;
	}

	default Supplier<T> assSupplier() {
		return this::invokeUnchecked;
	}

	default Try<T> cacheSuccess() {
		AtomicReference<T> reference = new AtomicReference<>();
		return () -> reference.updateAndGet(this::invokeIfNull);
	}

	default Try<T> cacheSuccessAndFailure() {
		AtomicReference<Either<T, Throwable>> reference = new AtomicReference<>();
		return () -> reference.updateAndGet(this::invokeIfNull).foldRight(Throwing::unchecked);
	}

	@Override
	default T call() throws Exception {
		T value = invokeUnchecked();
		System.out.println("value=" + value);
		return value;
	}

	default Try<Future<T>> dispatchAsync(Function<Try<T>, Future<T>> executor) {
		return () -> invokeAsync(executor);
	}

	default Try<T> dispatchAsyncAndWait(Function<Try<T>, Future<T>> executor) {
		return dispatchAsync(executor).mapSuccess(Throwing.ofApplied(f -> f.get()));
	}

	default Try<T> dispatchAsyncAndWait(Function<Try<T>, Future<T>> executor, long timeout, TimeUnit unit) {
		return dispatchAsync(executor).mapSuccess(Throwing.ofApplied(f -> f.get(timeout, unit)));
	}

	default Try<Throwable> failed() {
		return map(Throwing.of(UnsupportedOperationException::new).asFunction(), Function.identity());
	}

	default <X, E extends Throwable> Try<X> flatMap(Function<? super T, Try<X>> success, Function<? super E, Try<X>> failure, Class<E> failureType) {
		requireNonNull(success);
		requireNonNull(failure);
		requireNonNull(failureType);
		Function<Throwable, Try<? extends X>> elseFailure = e -> failureType.isInstance(e) ? failure.apply(failureType.cast(e)) : Throwing.unchecked(e);
		return () -> invokeReturnEither().fold(success, elseFailure).invokeChecked();
	}

	default <X> Try<X> flatMap(Function<? super T, Try<X>> success, Function<? super Throwable, Try<X>> failure) {
		requireNonNull(success);
		requireNonNull(failure);
		return () -> invokeReturnEither().fold(success, failure).invokeChecked();
	}

	default <E extends Throwable> Try<T> flatMapFailure(Function<? super E, Try<T>> failure, Class<E> failureType) {
		return flatMap(Function.<T>identity().andThen(Try::ofSuccess), failure, failureType);
	}

	default Try<T> flatMapFailure(Function<? super Throwable, Try<T>> failure) {
		return flatMap(Function.<T>identity().andThen(Try::ofSuccess), failure);
	}

	default <X> Try<X> flatMapSuccess(Function<? super T, Try<X>> success) {
		return flatMap(success, Throwing::unchecked);
	}

	default void ifPresent(Consumer<? super T> consumer) {
		invokeReturnEither().foldLeft(r -> {
			consumer.accept(r);
			return null;
		});
	}

	default Future<T> invokeAsync(Function<Try<T>, Future<T>> executor) {
		return executor.apply(this);
	}

	T invokeChecked() throws Throwable;

	default Either<T, Throwable> invokeIfNull(Either<T, Throwable> existing) {
		return existing != null ? existing : invokeReturnEither();
	}

	default T invokeIfNull(T existing) {
		return existing != null ? existing : invokeUnchecked();
	}

	default Either<T, Throwable> invokeReturnEither() {
		try {
			return Either.left(invokeChecked());
		} catch (Throwable e) {
			return Either.right(e);
		}
	}

	default Optional<T> invokeReturnOptional() {
		return invokeReturnEither().fold(Optional::of, e -> Optional.empty());
	}

	default T invokeUnchecked() {
		return invokeReturnEither().fold(Function.identity(), Throwing::unchecked);
	}

	default Try<T> logExceptions() {
		return visitFailure(e -> Logger.getAnonymousLogger().log(Level.SEVERE, e.getMessage(), e));
	}

	default Try<T> logExceptions(Class<?> loggingClass) {
		return logExceptions(loggingClass.getName());
	}

	default Try<T> logExceptions(String loggerName) {
		return visitFailure(e -> Logger.getLogger(loggerName).log(Level.SEVERE, e.getMessage(), e));
	}

	default <X, E extends Throwable> Try<X> map(Function<? super T, ? extends X> success, Function<? super E, ? extends X> failure, Class<E> failureType) {
		requireNonNull(success);
		requireNonNull(failure);
		requireNonNull(failureType);
		Function<Throwable, X> elseFailure = e -> failureType.isInstance(e) ? failure.apply(failureType.cast(e)) : Throwing.unchecked(e);
		return () -> invokeReturnEither().fold(success, elseFailure);
	}

	default <X> Try<X> map(Function<? super T, ? extends X> success, Function<? super Throwable, ? extends X> failure) {
		requireNonNull(success);
		requireNonNull(failure);
		return () -> invokeReturnEither().fold(success, failure);
	}

	default <E extends Throwable> Try<T> mapFailure(Function<? super E, ? extends T> failure, Class<E> failureType) {
		return map(Function.identity(), failure, failureType);
	}

	default Try<T> mapFailure(Function<? super Throwable, ? extends T> failure) {
		return map(Function.identity(), failure);
	}

	default <X> Try<X> mapSuccess(Function<? super T, ? extends X> success) {
		return map(success, Throwing::unchecked);
	}

	default Try<T> retry(int retryCount) {
		if (retryCount > 0) {
			return flatMapFailure(e -> retry(retryCount - 1));
		} else {
			return this;
		}
	}

	default Try<T> retry(int retryCount, Function<Try<T>, Future<T>> executor) throws Exception {
		if (retryCount > 0) {
			// return flatMapFailure(e -> dispatchAsyncAndWait(executor).flatMapFailure(e2 -> retry(retryCount - 1)));
			return () -> {
				Throwable exception = null;
				for (int i = 0; i < retryCount; i++) {
					try {
						return invokeChecked();
					} catch (Throwable e) {
						exception = e;
					}
				}
				throw exception;
			};
		} else {
			return this;
		}
	}

	default Try<T> visitFailure(Consumer<? super Throwable> consumer) {
		return mapFailure(e -> {
			consumer.accept(e);
			return Throwing.unchecked(e);
		});
	}
}
