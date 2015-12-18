package org.ddd4j.function;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.ddd4j.value.collection.Tpl;

@FunctionalInterface
public interface Process<I, O> {

	@FunctionalInterface
	interface Pipe<I, O> extends Process<I, O> {

		static <O> Pipe<Void, O> of(Callable<O> task) {
			requireNonNull(task);
			return i -> task.call();
		}

		@Override
		default CompletionStage<O> apply(I input) throws Exception {
			return CompletableFuture.completedFuture(process(input));
		}

		O process(I input) throws Exception;
	}

	class ScheduleableProcess<I, O> implements Process<I, O> {

		private final Process<I, O> delegate;
		private final ScheduledExecutorService executor;
		private final long interval;
		private final TimeUnit timeUnit;
		private ScheduledFuture<?> future;

		public ScheduleableProcess(Process<I, O> delegate, ScheduledExecutorService executor, long interval, TimeUnit timeUnit) {
			this.delegate = requireNonNull(delegate);
			this.executor = requireNonNull(executor);
			this.interval = requireNonNull(interval);
			this.timeUnit = requireNonNull(timeUnit);
			this.future = null;
		}

		@Override
		public CompletionStage<O> apply(I input) throws Exception {
			return delegate.apply(input);
		}

		@Override
		public void start() {
			if (future == null) {
				future = executor.scheduleWithFixedDelay(delegate::run, 0, interval, timeUnit);
			}
		}

		@Override
		public void stop() {
			if (future != null) {
				future.cancel(false);
				future = null;
			}
		}
	}

	@FunctionalInterface
	interface Source<O> extends Pipe<Void, O>, Callable<O> {

		@Override
		default O process(Void input) throws Exception {
			return call();
		}
	}

	static <O> Process<Void, O> monitor(ScheduledExecutorService executor, Callable<O> monitor, long interval, TimeUnit unit) {
		Process<Void, O> process = i -> CompletableFuture.completedFuture(monitor.call());
		ScheduledFuture<?> future = executor.scheduleWithFixedDelay(process::run, 0, interval, unit);
		return process;
	}

	default <T> Process<I, T> afterBoth(Pipe<? super O, T> other) {
		requireNonNull(other);
		return i -> apply(i).thenCompose(other::unchecked);
	}

	CompletionStage<O> apply(I input) throws Exception;

	default Process<I, O> either(Process<? super I, ? extends O> other) {
		return i -> apply(i).applyToEither(other.apply(i), Function.identity());
	}

	default void manager(Runnable starter, Runnable stopper) {
	}

	default CompletionStage<O> run() {
		return unchecked(null);
	}

	default void start() {
	}

	default void stop() {
	}

	default <T> Process<I, T> thenApply(Pipe<? super O, T> other) {
		requireNonNull(other);
		return i -> apply(i).thenApply(null);
	}

	default <T> Process<I, Tpl<O, T>> thenCombine(Process<? super I, T> other) {
		requireNonNull(other);
		return i -> apply(i).thenCombine(other.apply(i), Tpl::of);
	}

	default <T> Process<I, T> thenCompose(Process<? super O, T> other) {
		requireNonNull(other);
		return i -> apply(i).thenCompose(other::unchecked);
	}

	default CompletionStage<O> unchecked(I input) {
		try {
			return apply(input);
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}
}
