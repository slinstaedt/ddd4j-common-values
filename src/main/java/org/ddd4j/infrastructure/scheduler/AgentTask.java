package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ManagedBlocker;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

import org.ddd4j.Require;
import org.ddd4j.Throwing.TFunction;
import org.ddd4j.infrastructure.Promise;

public class AgentTask<T, R> implements Future<R>, Promise<R> {

	public static class Blocking<T, R> extends AgentTask<T, R> {

		public Blocking(Executor executor, TFunction<? super T, ? extends R> action) {
			super(executor, action);
		}

		@Override
		public boolean performOn(T state) {
			ManagedBlocker blocker = new ManagedBlocker() {

				@Override
				public boolean block() throws InterruptedException {
					return Blocking.super.performOn(state);
				}

				@Override
				public boolean isReleasable() {
					return isDone();
				}
			};
			try {
				ForkJoinPool.managedBlock(blocker);
			} catch (InterruptedException e) {
				failWith(e);
			}
			return isDone();
		}
	}

	private final Executor executor;
	private final TFunction<? super T, ? extends R> action;
	private final CompletableFuture<R> future;

	public AgentTask(Executor executor, TFunction<? super T, ? extends R> action) {
		this.executor = Require.nonNull(executor);
		this.action = Require.nonNull(action);
		this.future = new CompletableFuture<>();
	}

	@Override
	public <X> Promise<X> apply(BiFunction<Executor, CompletionStage<R>, CompletionStage<X>> fn) {
		return Promise.of(executor, fn.apply(executor, future));
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return future.cancel(mayInterruptIfRunning);
	}

	protected boolean failWith(Throwable exception) {
		return future.completeExceptionally(exception);
	}

	@Override
	public R get() throws InterruptedException, ExecutionException {
		return future.get();
	}

	@Override
	public R get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return future.get(timeout, unit);
	}

	@Override
	public boolean isCancelled() {
		return future.isCancelled();
	}

	@Override
	public boolean isDone() {
		return future.isDone();
	}

	public boolean performOn(T state) {
		if (future.isDone()) {
			return true;
		}
		try {
			R result = action.applyChecked(state);
			return future.complete(result);
		} catch (Throwable e) {
			return future.completeExceptionally(e);
		}
	}

	@Override
	public CompletionStage<R> toCompletionStage() {
		return future;
	}
}