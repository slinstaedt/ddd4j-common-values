package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.scheduler.BlockingTask.Trigger;
import org.ddd4j.spi.Service;
import org.ddd4j.value.Nothing;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.Throwing.Producer;
import org.ddd4j.value.Throwing.Task;
import org.ddd4j.value.Type;

@FunctionalInterface
public interface Scheduler extends Executor, Service<Scheduler, SchedulerProvider> {

	default <T> Agent<T> createAgent(T initialState) {
		return Agent.create(this, initialState);
	}

	default <T> T createAgentDecorator(Type<T> type, T initialState) {
		return AgentInvocationHandler.create(this, type, initialState);
	}

	default <T> Deferred<T> createDeferredPromise() {
		return new Deferred<>(this);
	}

	default <T> Promise<T> createOutcome(CompletionStage<T> stage) {
		return Promise.of(this, stage);
	}

	default <T> Promise<T> createOutcome(Future<T> future) {
		return Promise.ofBlocking(this, future);
	}

	default void schedulePeriodic(BlockingTask task) {
		Require.nonNull(task);
		Producer<Trigger> producer = () -> task.perform(1000, TimeUnit.MILLISECONDS);
		schedule(producer).handleException(task::handleException).whenCompleteSuccessfully(t -> t.apply(this, task));
	}

	default void executeBlockingTask(long timeout, TimeUnit unit) {
		try {
			unit.sleep(timeout);
		} catch (InterruptedException e) {
			Throwing.unchecked(e);
		}
	}

	default int getBurstProcessing() {
		return Integer.MAX_VALUE;
	}

	default <T> Promise<T> schedule(Producer<T> producer) {
		return schedule(producer, 0, TimeUnit.MILLISECONDS);
	}

	default <T> Promise<T> schedule(Producer<T> producer, long delay, TimeUnit unit) {
		Deferred<T> deferred = new Deferred<>(this);
		execute(() -> {
			long remaining = unit.toMillis(delay);
			while (remaining > 0) {
				long start = System.currentTimeMillis();
				executeBlockingTask(delay, unit);
				remaining += start - System.currentTimeMillis();
			}
			try {
				deferred.completeSuccessfully(producer.produce());
			} catch (Throwable e) {
				deferred.completeExceptionally(e);
			}

		});
		return deferred;
	}

	default Promise<Nothing> schedule(Task task) {
		return schedule(task, 0, TimeUnit.MILLISECONDS);
	}

	default <T> Promise<Nothing> schedule(Task task, long delay, TimeUnit unit) {
		return schedule(task.andThen(() -> Nothing.INSTANCE), delay, unit);
	}

	default Promise<Nothing> scheduleAtFixedRate(Task task, long initialDelay, long period, TimeUnit unit) {
		return schedule(new Task() {

			@Override
			public void perform() throws Exception {
				long plannedTimestamp = System.currentTimeMillis() + period;
				task.perform();
				long delay = plannedTimestamp - System.currentTimeMillis();
				if (delay < 0) {
					delay = 0;
				}
				schedule(this, delay, unit);
			}
		}, initialDelay, unit);
	}

	default Promise<Nothing> scheduleWithFixedDelay(Task task, long initialDelay, long delay, TimeUnit unit) {
		return schedule(new Task() {

			@Override
			public void perform() throws Exception {
				task.perform();
				schedule(this, delay, unit);
			}
		}, initialDelay, unit);
	}
}
