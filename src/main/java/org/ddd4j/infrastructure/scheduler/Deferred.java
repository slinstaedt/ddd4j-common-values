package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;

public class Deferred<T> implements Promise<T> {

	private final Executor executor;
	private final CompletableFuture<T> future;

	Deferred(Executor executor) {
		this.executor = Require.nonNull(executor);
		this.future = new CompletableFuture<>();
	}

	@Override
	public <X> Promise<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
		return Promise.of(executor, fn.apply(executor, future));
	}

	public boolean cancel(boolean mayInterruptIfRunning) {
		return future.cancel(mayInterruptIfRunning);
	}

	public void complete(T value, Throwable exception) {
		if (value != null && exception == null) {
			completeSuccessfully(value);
		} else if (exception != null && value == null) {
			completeExceptionally(exception);
		} else {
			throw new IllegalArgumentException("Value: " + value + ", Throwable: " + exception);
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