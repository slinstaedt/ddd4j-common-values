package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.TimeUnit;

import org.ddd4j.infrastructure.Promise;

public interface BlockingTask {

	interface Trigger {

		Trigger NOTHING = (s, t) -> {
		};

		Trigger RESCHEDULE = Scheduler::schedulePeriodic;

		void apply(Scheduler scheduler, BlockingTask task);
	};

	default Trigger handleException(Throwable exception) {
		return Trigger.NOTHING;
	}

	Promise<Trigger> perform(long timeout, TimeUnit unit) throws Exception;
}
