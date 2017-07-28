package org.ddd4j.infrastructure.scheduler;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.ddd4j.Require;
import org.ddd4j.Throwing.Producer;
import org.ddd4j.Throwing.Task;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.scheduler.BlockingExecutor.DelayedStage;
import org.ddd4j.infrastructure.scheduler.ScheduledTask.Rescheduler;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Key;
import org.ddd4j.value.Nothing;
import org.ddd4j.value.collection.Configuration;

public class Scheduler implements AutoCloseable {

	public enum PoolType {
		FORK_JOIN_POOL {

			@Override
			public Executor create(int size) {
				return Executors.newWorkStealingPool(size);
			}
		},
		SCHEDULED_THREAD_POOL {

			@Override
			public Executor create(int size) {
				return Executors.newScheduledThreadPool(size);
			}
		},
		SINGLE_THREADED {

			@Override
			public Executor create(int size) {
				return Runnable::run;
			}
		},
		THREAD_POOL {

			@Override
			public Executor create(int size) {
				return Executors.newFixedThreadPool(size);
			}
		};

		public abstract Executor create(int size);
	}

	public static final Configuration.Key<PoolType> POOL_TYPE = Configuration.keyOfEnum(PoolType.class, "pool.type",
			PoolType.FORK_JOIN_POOL);
	public static final Configuration.Key<Integer> POOL_SIZE = Configuration.keyOfInteger("pool.size",
			Runtime.getRuntime().availableProcessors());
	public static final Configuration.Key<Integer> BURST_PROCESSING = Configuration.keyOfInteger("burst", Integer.MAX_VALUE);
	public static final Configuration.Key<Long> MAX_BLOCKING_IN_MILLIS = Configuration.keyOfLong("maxBlockingInMillis", 2000L);
	public static final Key<Scheduler> KEY = Key.of(Scheduler.class, Scheduler::create);

	public static Scheduler create(Context context) {
		Executor executor = context.conf(POOL_TYPE).create(context.conf(POOL_SIZE));
		BlockingExecutor blockingExecutor = BlockingExecutor.blockingExecutor(executor, context.conf(MAX_BLOCKING_IN_MILLIS));
		return new Scheduler(blockingExecutor, context.conf(BURST_PROCESSING));
	}

	private final BlockingExecutor executor;
	private final int burstProcessing;

	public Scheduler(BlockingExecutor executor, int burstProcessing) {
		this.executor = Require.nonNull(executor);
		this.burstProcessing = Require.that(burstProcessing, burstProcessing > 0);
	}

	@Override
	public void close() {
		executor.close();
	}

	public <T> Agent<T> createAgent(T target) {
		return Agent.create(this, target);
	}

	public <T> Promise.Deferred<T> createDeferredPromise() {
		return Promise.deferred(executor);
	}

	public <T> Promise<T> createOutcome(CompletionStage<T> stage) {
		return Promise.of(executor, stage);
	}

	public <T> Promise<T> createOutcome(Future<T> future) {
		return Promise.ofFuture(executor, future);
	}

	public <T> Promise<T> execute(Producer<T> producer) {
		return schedule(producer, 0, TimeUnit.MILLISECONDS);
	}

	public Promise<Nothing> execute(Task task) {
		return schedule(task, 0, TimeUnit.MILLISECONDS);
	}

	public <T> Promise<T> execute(Blocked<T> blocked) {
		return Promise.of(executor, executor.execute(blocked));
	}

	public int getBurstProcessing() {
		return burstProcessing;
	}

	public Rescheduler reschedulerFor(ScheduledTask task) {
		return new Rescheduler(this, task);
	}

	public <T> Promise.Cancelable<T> schedule(Producer<T> producer, long delay, TimeUnit unit) {
		DelayedStage<T> stage = executor.schedule(producer, delay, unit);
		return new Promise.Delayed<>(executor, stage.getStage().toCompletableFuture(), stage.getDelayed());
	}

	public <T> Promise.Cancelable<Nothing> schedule(Task task, long delay, TimeUnit unit) {
		return schedule(task.andThen(() -> Nothing.INSTANCE), delay, unit);
	}
}
