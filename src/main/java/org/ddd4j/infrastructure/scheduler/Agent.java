package org.ddd4j.infrastructure.scheduler;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.value.Self;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.Throwing.TConsumer;
import org.ddd4j.value.Throwing.TFunction;

public abstract class Agent<T> {

	public static class Inherited<T extends Inherited<T>> extends Agent<T> implements Self<T> {

		public Inherited(Executor executor, int burst) {
			super(executor, burst);
		}

		@Override
		protected T getState() {
			return self();
		}
	}

	public static final class Transitioning<T> extends Agent<T> {

		private T state;

		public Transitioning(Executor executor, int burst, T initialState) {
			super(executor, burst);
			this.state = Require.nonNull(initialState);
		}

		@Override
		protected T getState() {
			return state;
		}

		public Promise<T> performTransition(TFunction<? super T, ? extends T> transition) {
			return scheduleIfNeeded(new Task<T, T>(getExecutor(), transition)).sync().whenComplete(this::updateState);
		}

		private void updateState(T state, Throwable exception) {
			this.state = exception != null ? Throwing.unchecked(exception) : state;
		}
	}

	public static <T> Transitioning<T> create(Scheduler scheduler, T initialState) {
		return new Transitioning<>(scheduler, scheduler.getBurstProcessing(), initialState);
	}

	private final Executor executor;
	private final int burst;
	private final Queue<Task<T, ?>> tasks;
	private final AtomicBoolean scheduled;

	protected Agent(Executor executor, int burst) {
		this.executor = Require.nonNull(executor);
		this.burst = Require.that(burst, burst > 0);
		this.tasks = new ConcurrentLinkedQueue<>();
		this.scheduled = new AtomicBoolean(false);
	}

	<R> R ask(TFunction<? super T, R> query) {
		return query.apply(getState());
	}

	public <R> Promise<R> execute(TFunction<? super T, ? extends R> task) {
		return scheduleIfNeeded(new Task<>(executor, task));
	}

	protected int getBurst() {
		return burst;
	}

	protected Executor getExecutor() {
		return executor;
	}

	protected abstract T getState();

	public Promise<T> perform(TConsumer<T> action) {
		return scheduleIfNeeded(new Task<>(Runnable::run, action.asFunction()));
	}

	private void run() {
		try {
			int iteration = 0;
			Task<T, ?> task = null;
			while (iteration++ < burst && (task = tasks.poll()) != null) {
				task.executeWith(getState());
			}
		} finally {
			scheduled.set(false);
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
