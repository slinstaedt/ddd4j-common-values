package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.ddd4j.value.Throwing;

public interface LoopedTask {

	default boolean keepRunning() {
		return true;
	}

	void loop(long duration, TimeUnit unit) throws Exception;

	default boolean rescheduleOn(Exception exception) {
		return false;
	}

	default void perform(Executor executor, long duration, TimeUnit unit) {
		try {
			loop(duration, unit);
			if (keepRunning()) {
				scheduleWith(executor, duration, unit);
			}
		} catch (Exception e) {
			if (rescheduleOn(e)) {
				scheduleWith(executor, duration, unit);
			} else {
				Throwing.unchecked(e);
			}
		}
	}

	default void scheduleWith(Executor executor, long duration, TimeUnit unit) {
		executor.execute(() -> perform(executor, duration, unit));
	}
}
