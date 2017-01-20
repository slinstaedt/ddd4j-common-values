package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public interface BlockingTask {

	interface Trigger {

		Trigger NOTHING = (s, t) -> {
		};

		Trigger RESCHEDULE = Scheduler::schedulePeriodic;

		void apply(Scheduler scheduler, BlockingTask task);
	};

	public abstract void apply(Consumer<BlockingTask> scheduler, BlockingTask task);

	default Trigger handleException(Throwable exception) {
		return Trigger.NOTHING;
	}

	Trigger perform(long timeout, TimeUnit unit) throws Exception;
}
