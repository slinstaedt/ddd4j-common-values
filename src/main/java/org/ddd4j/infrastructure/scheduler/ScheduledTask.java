package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.util.Require;

public interface ScheduledTask {

	class Rescheduler {

		private final Scheduler scheduler;
		private final ScheduledTask task;
		private final AtomicBoolean scheduled;

		public Rescheduler(Scheduler scheduler, ScheduledTask task) {
			this.scheduler = Require.nonNull(scheduler);
			this.task = Require.nonNull(task);
			this.scheduled = new AtomicBoolean();
		}

		public void doIfNecessary() {
			if (scheduled.compareAndSet(false, true)) {
				task.onScheduled(scheduler)
						.thenRun(() -> scheduled.set(false))
						.exceptionally(task::handleException)
						.whenCompleteSuccessfully(t -> t.handle(this));
			}
		}
	};

	interface Trigger {

		Trigger NOTHING = Rescheduler::getClass;
		Trigger RESCHEDULE = Rescheduler::doIfNecessary;

		void handle(Rescheduler rescheduler);
	}

	default Trigger handleException(Throwable exception) {
		return Trigger.NOTHING;
	}

	Promise<Trigger> onScheduled(Scheduler scheduler);
}
