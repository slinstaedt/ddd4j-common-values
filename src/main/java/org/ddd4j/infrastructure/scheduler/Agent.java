package org.ddd4j.infrastructure.scheduler;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
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

	public interface Task<T, R> {

		default Blocked<Task<T, R>> asBlocked() {
			return (t, u) -> this;
		}

		R perform(T target) throws Exception;
	}

	private class Job<R> {

		private final Blocked<? extends R> task;
		private final Promise.Deferred<R> deferred;

		Job(Blocked<? extends Task<? super T, ? extends R>> task, boolean blocked) {
			this.task = blocked ? task.managed(t -> t.perform(target), this::isDoneWithAll) : task.map(t -> t.perform(target));
			this.deferred = scheduler.createDeferredPromise();
		}

		Promise.Cancelable<R> getPromise() {
			return deferred;
		}

		boolean isDoneWithAll() {
			return deferred.isDone() && jobs.isEmpty();
		}

		boolean executeWithTimeout(long duration, TimeUnit unit) {
			if (deferred.isDone()) {
				return true;
			}
			try {
				R value = task.execute(duration, unit);
				return deferred.completeSuccessfully(value);
			} catch (Throwable e) {
				return deferred.completeExceptionally(e);
			}
		}
	}

	public static <T> Agent<T> create(Scheduler scheduler, T target) {
		return new Agent<>(scheduler, target);
	}

	private final Scheduler scheduler;
	private final T target;
	private final Queue<Job<?>> jobs;
	private final AtomicBoolean scheduled;

	public Agent(Scheduler scheduler, T target) {
		this.scheduler = Require.nonNull(scheduler);
		this.target = Require.nonNull(target);
		this.jobs = new ConcurrentLinkedQueue<>();
		this.scheduled = new AtomicBoolean(false);
	}

	public Promise.Cancelable<Nothing> execute(Action<? super T> action) {
		return perform(action);
	}

	public Promise.Cancelable<Nothing> executeBlocked(Blocked<Action<? super T>> action) {
		return performBlocked(action);
	}

	public <R> Promise.Cancelable<R> perform(Task<? super T, ? extends R> task) {
		return scheduleIfNeeded(new Job<R>(task.asBlocked(), false)).getPromise();
	}

	public <R> Promise.Cancelable<R> performBlocked(Blocked<? extends Task<? super T, R>> task) {
		return scheduleIfNeeded(new Job<>(task, true)).getPromise();
	}

	private boolean run(long duration, TimeUnit unit) {
		try {
			int remaining = scheduler.getBurstProcessing();
			Job<?> job = null;
			while (remaining-- > 0 && (job = jobs.poll()) != null) {
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
		jobs.offer(job);
		if (scheduled.compareAndSet(false, true)) {
			scheduler.execute(this::run);
		}
		return job;
	}
}
