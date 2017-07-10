package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.ddd4j.Throwing;
import org.ddd4j.Throwing.Producer;
import org.ddd4j.Throwing.Task;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.scheduler.BlockingTask.Rescheduler;
import org.ddd4j.spi.Key;
import org.ddd4j.value.Nothing;
import org.ddd4j.value.Type;
import org.ddd4j.value.collection.Configuration;

@FunctionalInterface
public interface Scheduler extends Executor {

	enum PoolType {
		SINGLE_THREADED {

			@Override
			public Scheduler create(int size) {
				return Runnable::run;
			}
		},
		FORK_JOIN_POOL {

			@Override
			public Scheduler create(int size) {
				return Executors.newWorkStealingPool(size)::execute;
			}
		},
		THREAD_POOL {

			@Override
			public Scheduler create(int size) {
				return Executors.newFixedThreadPool(size)::execute;
			}
		};

		public abstract Scheduler create(int size);
	}

	Configuration.Key<PoolType> POOL_TYPE = Configuration.keyOfEnum(PoolType.class, "pool.type", PoolType.SINGLE_THREADED);
	Configuration.Key<Integer> POOL_SIZE = Configuration.keyOfInteger("pool.size", Runtime.getRuntime().availableProcessors());
	Key<Scheduler> KEY = Key.of(Scheduler.class, ctx -> ctx.conf(POOL_TYPE).create(ctx.conf(POOL_SIZE)));

	default <T> Agent<T> createAgent(T initialState) {
		return Agent.create(this, initialState);
	}

	default <T> T createAgentDecorator(Type<T> type, T initialState) {
		return AgentInvocationHandler.create(this, type, initialState);
	}

	default <T> Promise.Deferred<T> createDeferredPromise() {
		return Promise.deferred(this);
	}

	default <T> Promise<T> createOutcome(CompletionStage<T> stage) {
		return Promise.of(this, stage);
	}

	default <T> Promise<T> createOutcome(Future<T> future) {
		return Promise.ofBlocking(this, future);
	}

	default void executeBlockingTask(long timeout, TimeUnit unit) {
		try {
			unit.sleep(timeout);
		} catch (InterruptedException e) {
			Throwing.unchecked(e);
		}
	}

	default long getBlockingTimeoutInMillis() {
		return 1000;
	}

	default int getBurstProcessing() {
		return Integer.MAX_VALUE;
	}

	default Rescheduler reschedulerFor(BlockingTask task) {
		return new Rescheduler(this, task);
	}

	default <T> Promise<T> schedule(Producer<T> producer) {
		return schedule(producer, 0, TimeUnit.MILLISECONDS);
	}

	default <T> Promise<T> schedule(Producer<T> producer, long delay, TimeUnit unit) {
		Promise.Deferred<T> deferred = new Promise.Deferred<>(this);
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
