package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.util.Require;
import org.ddd4j.util.collection.RingBuffer;
import org.ddd4j.value.Nothing;

public class Agent<T> {

	public interface Action<T> extends Task<T, Nothing> {

		void execute(T target) throws Exception;

		@Override
		default Nothing perform(T target) throws Exception {
			execute(target);
			return Nothing.INSTANCE;
		}
	}

	private class Job<R> {

		private final Blocked<Promise<R>> task;
		private final Promise.Deferred<R> deferred;

		Job(Blocked<? extends Task<? super T, Promise<R>>> task, boolean blocked) {
			this.task = blocked ? task.managed(t -> t.perform(target), this::isDoneWithAll) : task.map(t -> t.perform(target));
			this.deferred = scheduler.createDeferredPromise();
		}

		void executeWithTimeout(long duration, TimeUnit unit) {
			if (deferred.isDone()) {
				return;
			}
			try {
				task.execute(duration, unit).whenComplete(deferred::complete);
			} catch (Throwable e) {
				deferred.completeExceptionally(e);
			}
		}

		Promise.Cancelable<R> getPromise() {
			return deferred;
		}

		boolean isDoneWithAll() {
			return deferred.isDone() && jobs.isEmpty();
		}
	}

	public interface Task<T, R> {

		default Blocked<Task<T, R>> asBlocked() {
			return (d, u) -> this;
		}

		default Task<T, Promise<R>> asPromised() {
			return t -> Promise.completed(perform(t));
		}

		R perform(T target) throws Exception;
	}

	public static <T> Agent<T> create(Scheduler scheduler, T target, int jobBufferSize) {
		return new Agent<>(scheduler, target, jobBufferSize);
	}

	private final Scheduler scheduler;
	private final T target;
	private ScheduledTask scheduledTask;
	// TODO throw on overflow
	private final RingBuffer<Job<?>> jobs;
	private final AtomicBoolean scheduled;

	public Agent(Scheduler scheduler, T target, int jobBufferSize) {
		this.scheduler = Require.nonNull(scheduler);
		this.target = Require.nonNull(target);
		this.jobs = new RingBuffer<>(jobBufferSize);
		this.scheduled = new AtomicBoolean(false);
	}

	public Promise.Cancelable<Nothing> execute(Action<? super T> action) {
		return perform(action);
	}

	public Promise.Cancelable<Nothing> executeBlocked(Blocked<Action<? super T>> action) {
		return performBlocked(action);
	}

	public <R> Promise.Cancelable<R> perform(Task<? super T, R> task) {
		return scheduleIfNeeded(new Job<>(task.asPromised().asBlocked(), false)).getPromise();
	}

	public <R> Promise.Cancelable<R> performBlocked(Blocked<? extends Task<? super T, R>> blocked) {
		return scheduleIfNeeded(new Job<R>(blocked.map(Task::asPromised), true)).getPromise();
	}

	private boolean run(long duration, TimeUnit unit) {
		try {
			int remaining = scheduler.getBurstProcessing();
			Job<?> job = null;
			while (remaining-- > 0 && (job = jobs.getOrNull()) != null) {
				job.executeWithTimeout(duration, unit);
			}
		} finally {
			if (jobs.isEmpty()) {
				scheduled.set(false);
			} else {
				scheduler.execute(this::run);
			}
		}
		return jobs.isEmpty();
	}

	private <R> Job<R> scheduleIfNeeded(Job<R> job) {
		jobs.put(job);
		if (scheduled.compareAndSet(false, true)) {
			scheduler.execute(this::run);
		}
		return job;
	}
}
