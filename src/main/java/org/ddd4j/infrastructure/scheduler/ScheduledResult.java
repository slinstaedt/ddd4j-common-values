package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

@FunctionalInterface
public interface ScheduledResult<T> {

	<X> ScheduledResult<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn);

	default <U> ScheduledResult<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
		return apply((e, s) -> s.handleAsync(fn, e));
	}

	default ScheduledResult<Void> thenAccept(Consumer<? super T> action) {
		return apply((e, s) -> s.thenAcceptAsync(action, e));
	}

	default <U> ScheduledResult<U> thenApply(Function<? super T, ? extends U> fn) {
		return apply((e, s) -> s.thenApplyAsync(fn, e));
	}

	default ScheduledResult<Void> thenRun(Runnable action) {
		return apply((e, s) -> s.thenRunAsync(action, e));
	}

	default ScheduledResult<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
		return apply((e, s) -> s.whenCompleteAsync(action, e));
	}
}
