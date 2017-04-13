package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

import org.ddd4j.Require;
import org.ddd4j.Throwing.TFunction;
import org.ddd4j.infrastructure.Promise;

public class Task<T, R> implements Future<R>, Promise<R> {

	private final Executor executor;
	private final TFunction<? super T, ? extends R> action;
	private final CompletableFuture<R> future;

	public Task(Executor executor, TFunction<? super T, ? extends R> action) {
		this.executor = Require.nonNull(executor);
		this.action = Require.nonNull(action);
		this.future = new CompletableFuture<>();
	}

	@Override
	public <X> X apply(BiFunction<Executor, CompletionStage<R>, X> fn) {
		return fn.apply(executor, future);
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return future.cancel(mayInterruptIfRunning);
	}

	public boolean executeWith(T state) {
		if (future.isCancelled()) {
			return false;
		}
		try {
			R result = action.applyChecked(state);
			return future.complete(result);
		} catch (Throwable e) {
			return future.completeExceptionally(e);
		}
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

	@Override
	public CompletionStage<R> toCompletionStage() {
		return future;
	}
}