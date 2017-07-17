package org.ddd4j.infrastructure.scheduler;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
import org.ddd4j.Throwing.TConsumer;
import org.ddd4j.Throwing.TFunction;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.value.Self;

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
			return scheduleIfNeeded(new AgentTask<T, T>(getExecutor(), transition)).sync().whenComplete(this::updateState);
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
	private final Queue<AgentTask<T, ?>> tasks;
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

	/**
	 * Will return a {@link Promise}, that runs with the {@link Scheduler}'s thread.
	 *
	 * @param task
	 * @return
	 */
	public <R> Promise<R> execute(TFunction<? super T, ? extends R> task) {
		return scheduleIfNeeded(new AgentTask<>(executor, task));
	}

	public <R> Promise<R> executeBlocked(TFunction<? super T, ? extends R> task) {
		return scheduleIfNeeded(new AgentTask.Blocking<>(executor, task));
	}

	protected int getBurst() {
		return burst;
	}

	protected Executor getExecutor() {
		return executor;
	}

	protected abstract T getState();

	/**
	 * Will return a {@link Promise}, that runs with the {@link Agent}'s thread.
	 *
	 * @param action
	 * @return
	 */
	public Promise<T> perform(TConsumer<T> action) {
		return scheduleIfNeeded(new AgentTask<>(executor, action.asFunction()));
	}

	public Promise<T> performBlocked(TConsumer<T> action) {
		return scheduleIfNeeded(new AgentTask.Blocking<>(executor, action.asFunction()));
	}

	private void run() {
		try {
			int remaining = getBurst();
			AgentTask<T, ?> task = null;
			while (remaining-- > 0 && (task = tasks.poll()) != null) {
				task.performOn(getState());
			}
		} finally {
			if (tasks.isEmpty()) {
				scheduled.set(false);
			} else {
				executor.execute(this::run);
			}
		}
	}

	protected final <R> AgentTask<T, R> scheduleIfNeeded(AgentTask<T, R> task) {
		tasks.offer(task);
		if (scheduled.compareAndSet(false, true)) {
			executor.execute(this::run);
		}
		return task;
	}
}
