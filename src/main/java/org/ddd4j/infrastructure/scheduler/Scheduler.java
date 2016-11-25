package org.ddd4j.infrastructure.scheduler;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.Throwing.TSupplier;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

@FunctionalInterface
public interface Scheduler extends Executor {

	default <T> ScheduledResult<T> wrap(CompletionStage<T> stage) {
		return new ScheduledResult<T>() {

			@Override
			public <X> ScheduledResult<X> apply(BiFunction<Executor, CompletionStage<T>, CompletionStage<X>> fn) {
				return wrap(fn.apply(Scheduler.this, stage));
			}
		};
	}

	default <T> CompletionStage<T> perform(Callable<T> callable) {
		Require.nonNull(callable);
		return CompletableFuture.supplyAsync(Throwing.ofSupplied(callable::call), this);
	}

	default <T> CompletionStage<T> perform(Task<T> task) {
		Require.nonNull(task);
		return CompletableFuture.supplyAsync(task::executeUnchecked, this);
	}

	default <T> Publisher<T> perform(TSupplier<T> supplier, AutoCloseable closer) {
		ScheduledProcessor<T> processor = new ScheduledProcessor<>(this);
		processor.onSubscribe(new Subscription() {

			private long requested = 0;

			@Override
			public void request(long n) {
				requested += n;
				if (requested < 0) {
					requested = Long.MAX_VALUE;
				}
			}

			@Override
			public void cancel() {
				// TODO Auto-generated method stub

			}
		});
		return processor;
	}

	default <T> Publisher<T> perform(LongFunction<CompletionStage<List<T>>> reader, AutoCloseable closer) {
		ScheduledProcessor<T> stream = new ScheduledProcessor<>(this);
		processor.onSubscribe(new Subscription() {

			@Override
			public void cancel() {
				try {
					closer.close();
				} catch (Exception e) {
					Throwing.unchecked(e);
				}
			}

			@Override
			public void request(long n) {
				reader.apply(n).whenComplete(this::handle);
			}

			private void handle(List<T> results, Throwable exception) {
				if (results != null) {
					if (results.isEmpty()) {
						// TODO
					} else {
						results.forEach(processor::onNext);
					}
				} else if (exception != null) {
					processor.onError(exception);
				}
			}
		});
		return processor;
	}
}
