package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Outcome;

public class CompletableOutcome<T> implements Outcome<T> {

	private final Executor executor;
	private final CompletableFuture<T> future;

	CompletableOutcome(Executor executor) {
		this.executor = Require.nonNull(executor);
		this.future = new CompletableFuture<>();
	}

	@Override
	public <X> Outcome<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
		return Outcome.of(executor, fn.apply(executor, future));
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
	public Outcome<T> withExecutor(Executor executor) {
		return Outcome.of(executor, future);
	}
}