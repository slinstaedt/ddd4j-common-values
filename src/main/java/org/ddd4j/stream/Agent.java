package org.ddd4j.stream;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing.TConsumer;
import org.ddd4j.value.Throwing.TFunction;

public class Agent<T> {

	private final Queue<TFunction<? super T, ? extends T>> tasks;
	private volatile AtomicBoolean scheduled;
	private T state;

	public Agent(T initialState) {
		this.state = Require.nonNull(initialState);
		this.tasks = new ConcurrentLinkedQueue<>();
		this.scheduled = new AtomicBoolean(false);
	}

	public void schedule(Executor executor, TConsumer<T> task) {
		schedule(executor, task.asFunction());
	}

	public void schedule(Executor executor, TFunction<? super T, ? extends T> task) {
		tasks.offer(Require.nonNull(task));
		if (scheduled.compareAndSet(false, true)) {
			executor.execute(this::run);
		}
	}

	private void run() {
		try {
			TFunction<? super T, ? extends T> task = null;
			while ((task = tasks.poll()) != null) {
				state = task.apply(state);
			}
		} finally {
			scheduled.set(false);
		}
	}
}
