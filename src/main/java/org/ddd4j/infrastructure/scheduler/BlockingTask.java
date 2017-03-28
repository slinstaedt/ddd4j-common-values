package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;

public interface BlockingTask {

	interface Rescheduler {

		static Rescheduler create(Scheduler scheduler, BlockingTask task) {
			Require.nonNullElements(scheduler, task);
			return new Rescheduler() {

				private final AtomicBoolean scheduled = new AtomicBoolean();

				@Override
				public void doIfNecessary() {
					if (scheduled.compareAndSet(false, true)) {
						task.scheduleWith(scheduler, scheduler.getBlockingTimeoutInMillis(), TimeUnit.MILLISECONDS)
								.whenComplete((t, e) -> scheduled.set(false))
								.handleException(task::handleException)
								.whenCompleteSuccessfully(t -> t.handle(this));
					}
				}
			};
		}

		void doIfNecessary();
	};

	interface Trigger {

		Trigger NOTHING = r -> {
		};

		Trigger RESCHEDULE = Rescheduler::doIfNecessary;

		static Trigger onCallback(Consumer<Rescheduler> reschedulerConsumer) {
			return Require.nonNull(reschedulerConsumer)::accept;
		}

		void handle(Rescheduler rescheduler);
	}

	default Trigger handleException(Throwable exception) {
		return Trigger.NOTHING;
	}

	Promise<Trigger> scheduleWith(Executor executor, long timeout, TimeUnit unit);
}
