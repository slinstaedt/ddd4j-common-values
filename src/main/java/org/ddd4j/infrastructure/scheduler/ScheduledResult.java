package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.Throwing.TFunction;

public class ScheduledResult<T> {

	private final Executor executor;
	private final CompletionStage<T> stage;

	public ScheduledResult(Executor executor, CompletionStage<T> stage) {
		this.executor = Require.nonNull(executor);
		this.stage = Require.nonNull(stage);
	}

	private <X> ScheduledResult<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
		return new ScheduledResult<>(executor, fn.apply(executor, stage));
	}

	public <U> ScheduledResult<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
		return apply((e, s) -> s.handleAsync(fn, e));
	}

	public ScheduledResult<T> handleException(TFunction<? super Throwable, T> fn) {
		return handle((t, e) -> e != null ? fn.apply(e) : t);
	}

	public <X> ScheduledResult<X> handleSuccess(TFunction<? super T, X> fn) {
		return handle((t, e) -> e != null ? Throwing.unchecked(e) : fn.apply(t));
	}

	public ScheduledResult<Void> thenAccept(Consumer<? super T> action) {
		return apply((e, s) -> s.thenAcceptAsync(action, e));
	}

	public <U> ScheduledResult<U> thenApply(Function<? super T, ? extends U> fn) {
		return apply((e, s) -> s.thenApplyAsync(fn, e));
	}

	public ScheduledResult<Void> thenRun(Runnable action) {
		return apply((e, s) -> s.thenRunAsync(action, e));
	}

	public ScheduledResult<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
		return apply((e, s) -> s.whenCompleteAsync(action, e));
	}
}
