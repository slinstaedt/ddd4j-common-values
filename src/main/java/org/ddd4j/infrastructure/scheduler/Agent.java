package org.ddd4j.infrastructure.scheduler;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing.TConsumer;
import org.ddd4j.value.Throwing.TFunction;

public class Agent<T> {

	private static class Task<T, R> extends CompletableFuture<R> {

		private final TFunction<? super T, ? extends R> action;

		Task(TFunction<? super T, ? extends R> action) {
			this.action = Require.nonNull(action);
		}

		void run(T state) {
			try {
				complete(action.applyChecked(state));
			} catch (Exception e) {
				completeExceptionally(e);
			}
		}
	}

	private final Executor executor;
	private final Queue<Agent.Task<T, ?>> tasks;
	private final AtomicBoolean scheduled;

	private volatile T state;
	private volatile Optional<Thread> runner;

	public Agent(Executor executor, T initialState) {
		this.executor = Require.nonNull(executor);
		this.tasks = new ConcurrentLinkedQueue<>();
		this.scheduled = new AtomicBoolean(false);
		this.state = Require.nonNull(initialState);
		this.runner = Optional.empty();
	}

	private <R> Task<T, R> scheduleIfNeeded(Task<T, R> task) {
		tasks.offer(task);
		if (scheduled.compareAndSet(false, true)) {
			executor.execute(this::run);
		}
		return task;
	}

	public <R> CompletionStage<R> ask(TFunction<? super T, R> query) {
		return scheduleIfNeeded(new Task<>(query));
	}

	public void perform(TConsumer<T> task) {
		scheduleIfNeeded(new Task<>(task.asFunction()));
	}

	public CompletionStage<T> perform(TFunction<? super T, ? extends T> task) {
		return scheduleIfNeeded(new Task<T, T>(task)).whenComplete((t, e) -> {
			if (t != null) {
				state = t;
			}
		});
	}

	public boolean isRunning() {
		return runner.isPresent();
	}

	public void interrupt() {
		runner.ifPresent(Thread::interrupt);
	}

	private void run() {
		runner = Optional.of(Thread.currentThread());
		T current = state;
		try {
			Task<T, ?> task = null;
			while ((task = tasks.poll()) != null) {
				task.apply(current);
			}
		} finally {
			state = current;
			scheduled.set(false);
			runner = Optional.empty();
		}
	}
}
