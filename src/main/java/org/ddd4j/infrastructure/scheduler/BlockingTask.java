package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;

public interface BlockingTask {

	class Rescheduler {

		private final Scheduler scheduler;
		private final BlockingTask task;
		private final AtomicBoolean scheduled;

		public Rescheduler(Scheduler scheduler, BlockingTask task) {
			this.scheduler = Require.nonNull(scheduler);
			this.task = Require.nonNull(task);
			this.scheduled = new AtomicBoolean();
		}

		public void doIfNecessary() {
			if (scheduled.compareAndSet(false, true)) {
				task.executeWith(scheduler, scheduler.getBlockingTimeoutInMillis(), TimeUnit.MILLISECONDS)
						.thenRun(() -> scheduled.set(false))
						.exceptionally(task::handleException)
						.whenCompleteSuccessfully(t -> t.handle(this));
			}
		}
	};

	interface Trigger {

		Trigger NOTHING = r -> {
		};

		Trigger RESCHEDULE = Rescheduler::doIfNecessary;

		void handle(Rescheduler rescheduler);
	}

	default Trigger handleException(Throwable exception) {
		return Trigger.NOTHING;
	}

	Promise<Trigger> executeWith(Executor executor, long timeout, TimeUnit unit);
}
