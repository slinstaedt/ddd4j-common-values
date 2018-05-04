package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.ddd4j.util.Require;
import org.ddd4j.util.Throwing.TConsumer;
import org.ddd4j.util.Throwing.TFunction;

@FunctionalInterface
public interface Blocked<T> {

	class Managed<I, O> implements Blocked<O>, ForkJoinPool.ManagedBlocker {

		private final Blocked<I> delegate;
		private final TFunction<? super I, ? extends O> blocking;
		private final BooleanSupplier isDone;
		private I input;
		private O output;

		public Managed(Blocked<I> delegate, TFunction<? super I, ? extends O> blocking, BooleanSupplier isDone) {
			this.delegate = Require.nonNull(delegate);
			this.blocking = Require.nonNull(blocking);
			this.isDone = Require.nonNull(isDone);
		}

		@Override
		public boolean block() throws InterruptedException {
			output = blocking.apply(input);
			return isReleasable();
		}

		@Override
		public O execute(long duration, TimeUnit unit) throws Exception {
			input = delegate.execute(duration, unit);
			ForkJoinPool.managedBlock(this);
			return output;
		}

		@Override
		public boolean isReleasable() {
			return isDone.getAsBoolean();
		}
	}

	T execute(long duration, TimeUnit unit) throws Exception;

	default <X> Blocked<X> managed(TFunction<? super T, ? extends X> blocking, BooleanSupplier isDone) {
		return new Managed<>(this, blocking, isDone);
	}

	default <X> Blocked<X> map(TFunction<? super T, ? extends X> mapper) {
		Require.nonNull(mapper);
		return (duration, unit) -> mapper.applyChecked(execute(duration, unit));
	}

	default Blocked<T> withListener(TConsumer<? super T> success, TConsumer<? super Throwable> failure) {
		Require.nonNulls(success, failure);
		return (duration, unit) -> {
			try {
				T value = execute(duration, unit);
				success.acceptChecked(value);
				return value;
			} catch (Throwable e) {
				failure.acceptChecked(e);
				throw e;
			}
		};
	}
}
