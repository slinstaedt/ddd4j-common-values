package org.ddd4j.infrastructure.scheduler;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Self;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.Throwing.TConsumer;
import org.ddd4j.value.Throwing.TFunction;

public abstract class Agent<T> {

	public static class Inherited<T extends Inherited<T>> extends Agent<T> implements Self<T> {

		public Inherited(Executor executor) {
			super(executor);
		}

		@Override
		protected T getState() {
			return self();
		}
	}

	private static class Task<T, R> extends CompletableFuture<R> {

		private final TFunction<? super T, ? extends R> action;

		Task(TFunction<? super T, ? extends R> action) {
			this.action = Require.nonNull(action);
		}

		void performOn(T state) {
			try {
				complete(action.applyChecked(state));
			} catch (Exception e) {
				completeExceptionally(e);
			}
		}
	}

	public static final class Transitioning<T> extends Agent<T> {

		private T state;

		public Transitioning(Executor executor, T initialState) {
			super(executor);
			this.state = Require.nonNull(initialState);
		}

		@Override
		protected T getState() {
			return state;
		}

		public CompletionStage<T> performTransition(TFunction<? super T, ? extends T> transition) {
			return scheduleIfNeeded(new Task<T, T>(transition)).whenComplete(this::updateState);
		}

		private void updateState(T state, Throwable exception) {
			this.state = exception != null ? Throwing.unchecked(exception) : state;
		}
	}

	public static <T> Transitioning<T> create(Executor executor, T initialState) {
		return new Transitioning<>(executor, initialState);
	}

	private final Executor executor;
	private final Queue<Task<T, ?>> tasks;
	private final AtomicBoolean scheduled;

	private volatile Optional<Thread> runner;

	protected Agent(Executor executor) {
		this.executor = Require.nonNull(executor);
		this.tasks = new ConcurrentLinkedQueue<>();
		this.scheduled = new AtomicBoolean(false);
		this.runner = Optional.empty();
	}

	<R> R ask(TFunction<? super T, R> query) {
		return query.apply(getState());
	}

	public <R> CompletionStage<R> execute(TFunction<? super T, R> task) {
		return scheduleIfNeeded(new Task<>(task));
	}

	public Executor getExecutor() {
		return executor;
	}

	protected abstract T getState();

	public void interrupt() {
		runner.ifPresent(Thread::interrupt);
	}

	public boolean isRunning() {
		return runner.isPresent();
	}

	public CompletionStage<T> perform(TConsumer<T> action) {
		return scheduleIfNeeded(new Task<>(action.asFunction()));
	}

	private void run() {
		runner = Optional.of(Thread.currentThread());
		try {
			Task<T, ?> task = null;
			while ((task = tasks.poll()) != null) {
				task.performOn(getState());
			}
		} finally {
			scheduled.set(false);
			runner = Optional.empty();
		}
	}

	protected <R> Task<T, R> scheduleIfNeeded(Task<T, R> task) {
		tasks.offer(task);
		if (scheduled.compareAndSet(false, true)) {
			executor.execute(this::run);
		}
		return task;
	}
}
