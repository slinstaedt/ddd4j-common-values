package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing.TFunction;

public class Task<T, R> implements CompletableResult<R> {

	private final Executor executor;
	private final TFunction<? super T, ? extends R> action;
	private final CompletableFuture<R> future;

	public Task(Executor executor, TFunction<? super T, ? extends R> action) {
		this.executor = Require.nonNull(executor);
		this.action = Require.nonNull(action);
		this.future = new CompletableFuture<>();
	}

	@Override
	public <X> CompletableResult<X> apply(BiFunction<Executor, CompletionStage<R>, CompletionStage<X>> fn) {
		return CompletableResult.of(executor, fn.apply(executor, future));
	}

	public boolean cancel(boolean mayInterruptIfRunning) {
		return future.cancel(mayInterruptIfRunning);
	}

	public boolean executeWith(T state) {
		if (future.isCancelled()) {
			return false;
		}
		try {
			return future.complete(action.applyChecked(state));
		} catch (Exception e) {
			return future.completeExceptionally(e);
		}
	}
}