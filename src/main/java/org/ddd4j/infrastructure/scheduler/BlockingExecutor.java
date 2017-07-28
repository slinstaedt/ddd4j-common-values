package org.ddd4j.infrastructure.scheduler;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.ObjLongConsumer;
import java.util.stream.Collectors;

import org.ddd4j.Require;
import org.ddd4j.Throwing.Producer;
import org.ddd4j.Throwing.Task;
import org.ddd4j.value.Nothing;

public interface BlockingExecutor extends Executor, AutoCloseable {

	class CallableBasedScheduledFuture<T> extends FutureTask<T> implements RunnableScheduledFuture<T> {

		private static long now() {
			return System.currentTimeMillis();
		}

		private final ObjLongConsumer<LongFunction<CallableBasedScheduledFuture<T>>> delayToNextRun;
		private long nextRunInMillis;

		public CallableBasedScheduledFuture(Callable<T> callable, long timeInMillis) {
			this(callable, timeInMillis, null);
		}

		public CallableBasedScheduledFuture(Callable<T> callable, long timeInMillis,
				ObjLongConsumer<LongFunction<CallableBasedScheduledFuture<T>>> delayToNextRun) {
			super(callable);
			this.delayToNextRun = delayToNextRun;
			this.nextRunInMillis = timeInMillis;
		}

		@Override
		public int compareTo(java.util.concurrent.Delayed o) {
			return Long.compare(this.getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
		}

		@Override
		public long getDelay(TimeUnit unit) {
			return unit.convert(nextRunInMillis - now(), TimeUnit.MILLISECONDS);
		}

		public void ifOverdueOrElse(Consumer<CallableBasedScheduledFuture<T>> overdue, Consumer<CallableBasedScheduledFuture<T>> orElse) {
			if (isOverdue()) {
				overdue.accept(this);
			} else {
				orElse.accept(this);
			}
		}

		public boolean isOverdue() {
			return getDelay(TimeUnit.MILLISECONDS) <= 0;
		}

		@Override
		public boolean isPeriodic() {
			return delayToNextRun != null;
		}

		private CallableBasedScheduledFuture<T> nextRunAt(long timeInMillis) {
			nextRunInMillis = timeInMillis;
			return this;
		}

		@Override
		public void run() {
			if (!isOverdue()) {
				return;
			} else if (isPeriodic()) {
				long started = now();
				super.runAndReset();
				delayToNextRun.accept(this::nextRunAt, now() - started);
			} else {
				super.run();
			}
		}
	}

	class DelayedStage<T> {

		private final CompletionStage<T> stage;
		private final Delayed delayed;

		public DelayedStage(CompletionStage<T> stage, Delayed delayed) {
			this.stage = Require.nonNull(stage);
			this.delayed = Require.nonNull(delayed);
		}

		public Delayed getDelayed() {
			return delayed;
		}

		public CompletionStage<T> getStage() {
			return stage;
		}
	}

	class WrappedExecutor extends AbstractExecutorService implements ExecutorService {

		private class WrappedRunnable implements Runnable {

			private final Runnable delegate;
			private Thread currentThread;

			WrappedRunnable(Runnable delegate) {
				this.delegate = Require.nonNull(delegate);
				runnables.add(this);
			}

			void interruptIfRunning() {
				Thread thread = currentThread;
				if (thread != null) {
					thread.interrupt();
				}
			}

			@Override
			public void run() {
				if (shutdown) {
					return;
				}
				try {
					currentThread = Thread.currentThread();
					delegate.run();
				} finally {
					currentThread = null;
					runnables.remove(this);
				}
			}
		}

		private static final long WAIT_INTERVAL_MILLIS = 250;

		private final Executor delegate;
		private final List<WrappedRunnable> runnables;
		private volatile boolean shutdown;

		public WrappedExecutor(Executor delegate) {
			this.delegate = Require.nonNull(delegate);
			this.runnables = Collections.synchronizedList(new LinkedList<>());
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
			long millis = unit.toMillis(timeout);
			while (millis > 0 && !runnables.isEmpty()) {
				Thread.sleep(millis < WAIT_INTERVAL_MILLIS ? millis : WAIT_INTERVAL_MILLIS);
				millis -= WAIT_INTERVAL_MILLIS;
			}
			return runnables.isEmpty();
		}

		@Override
		public void execute(Runnable command) {
			if (shutdown) {
				throw new RejectedExecutionException("Due to shutdown");
			} else {
				delegate.execute(new WrappedRunnable(command));
			}
		}

		@Override
		public boolean isShutdown() {
			return shutdown;
		}

		@Override
		public boolean isTerminated() {
			return shutdown && runnables.isEmpty();
		}

		@Override
		public void shutdown() {
			shutdown = true;
		}

		@Override
		public List<Runnable> shutdownNow() {
			shutdown = true;
			runnables.forEach(WrappedRunnable::interruptIfRunning);
			List<Runnable> awaiting = runnables.stream().map(w -> w.delegate).collect(Collectors.toList());
			runnables.clear();
			return awaiting;
		}
	}

	class WrappedExecutorService extends AbstractExecutorService implements ScheduledExecutorService {

		private final ExecutorService delegate;
		private final long maxBlockingInMillis;
		private final Queue<CallableBasedScheduledFuture<?>> delayed;
		private final AtomicReference<Thread> sleeper;

		public WrappedExecutorService(ExecutorService delegate, long maxBlockingInMillis) {
			this.delegate = Require.nonNull(delegate);
			this.maxBlockingInMillis = Require.that(maxBlockingInMillis, t -> t > 0);
			this.delayed = new PriorityBlockingQueue<>();
			this.sleeper = new AtomicReference<>();
		}

		private void addDelayed(CallableBasedScheduledFuture<?> candidate) {
			CallableBasedScheduledFuture<?> next = delayed.peek();
			delayed.add(candidate);
			if (sleeper.get() == null || next == null || candidate.compareTo(next) < 0) {
				delegate.execute(this::sleepLoop);
			}
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
			return delegate.awaitTermination(timeout, unit);
		}

		private long checkBlocked() {
			return maxBlock(nextDelay());
		}

		@Override
		public void execute(Runnable command) {
			delegate.execute(command);
		}

		@Override
		public boolean isShutdown() {
			return delegate.isShutdown();
		}

		@Override
		public boolean isTerminated() {
			return delegate.isTerminated();
		}

		private long maxBlock(long millis) {
			return millis > 0 && millis < maxBlockingInMillis ? millis : maxBlockingInMillis;
		}

		private long nextDelay() {
			CallableBasedScheduledFuture<?> next = null;
			long delay = -1;
			while ((next = delayed.peek()) != null && (delay = next.getDelay(TimeUnit.MILLISECONDS)) <= 0) {
				if ((next = delayed.poll()) != null) {
					next.ifOverdueOrElse(delegate::execute, delayed::add);
				}
			}
			return delay;
		}

		@Override
		public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
			CallableBasedScheduledFuture<V> future = new CallableBasedScheduledFuture<>(callable, unit.toMillis(delay));
			future.ifOverdueOrElse(delegate::execute, this::addDelayed);
			return future;
		}

		@Override
		public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
			return schedule(Executors.callable(command), delay, unit);
		}

		@Override
		public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
			CallableBasedScheduledFuture<?> future = new CallableBasedScheduledFuture<>(Executors.callable(command),
					unit.toMillis(initialDelay), (s, d) -> addDelayed(s.apply(unit.toMillis(period) - d)));
			future.ifOverdueOrElse(delegate::execute, this::addDelayed);
			return future;
		}

		@Override
		public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
			CallableBasedScheduledFuture<?> future = new CallableBasedScheduledFuture<>(Executors.callable(command),
					unit.toMillis(initialDelay), (s, d) -> addDelayed(s.apply(unit.toMillis(delay))));
			future.ifOverdueOrElse(delegate::execute, this::addDelayed);
			return future;
		}

		@Override
		public void shutdown() {
			delegate.shutdown();
		}

		@Override
		public List<Runnable> shutdownNow() {
			return delegate.shutdownNow();
		}

		private void sleepLoop() {
			Thread previous = sleeper.get();
			if (previous != null) {
				previous.interrupt();
			}

			sleeper.set(Thread.currentThread());
			long delay = -1;
			while (Thread.currentThread() == sleeper.get() && (delay = nextDelay()) > 0) {
				try {
					Thread.sleep(maxBlock(delay));
				} catch (InterruptedException e) {
					// ignore
				}
			}
			sleeper.compareAndSet(Thread.currentThread(), null);
		}
	}

	class WrappedScheduledExecutorService implements BlockingExecutor {

		private final ScheduledExecutorService delegate;
		private final long blockingInMillis;

		public WrappedScheduledExecutorService(ScheduledExecutorService delegate, long blockingInMillis) {
			this.delegate = Require.nonNull(delegate);
			this.blockingInMillis = Require.that(blockingInMillis, t -> t > 0);
		}

		private long block() {
			if (delegate instanceof WrappedExecutorService) {
				return ((WrappedExecutorService) delegate).checkBlocked();
			} else {
				return blockingInMillis;
			}
		}

		@Override
		public void close() {
			delegate.shutdown();
		}

		@Override
		public <T> CompletableFuture<T> execute(Blocked<T> blocked) {
			CompletableFuture<T> stage = future();
			Blocked<T> listening = blocked.withListener(stage::complete, stage::completeExceptionally);
			Task blocking = () -> listening.execute(block(), TimeUnit.MILLISECONDS);
			delegate.execute(blocking);
			return stage;
		}

		@Override
		public void execute(Runnable command) {
			delegate.execute(command);
		}

		private <T> CompletableFuture<T> future() {
			CompletableFuture<T> future = new CompletableFuture<>();
			future.whenComplete((t, ex) -> block());
			return future;
		}

		@Override
		public <T> DelayedStage<T> schedule(Producer<T> producer, long delay, TimeUnit unit) {
			CompletableFuture<T> stage = future();
			Producer<T> listening = producer.withListener(stage::complete, stage::completeExceptionally);
			ScheduledFuture<T> delayed = delegate.schedule(listening, delay, unit);
			return new DelayedStage<>(stage, delayed);
		}
	}

	static BlockingExecutor blockingExecutor(Executor executor, long maxBlockingInMillis) {
		if (executor instanceof BlockingExecutor) {
			return (BlockingExecutor) executor;
		} else {
			return new WrappedScheduledExecutorService(scheduledExecutorService(executor, maxBlockingInMillis), maxBlockingInMillis);
		}
	}

	static ExecutorService executorService(Executor executor) {
		if (executor instanceof ExecutorService) {
			return (ExecutorService) executor;
		} else {
			return new WrappedExecutor(executor);
		}
	}

	static ScheduledExecutorService scheduledExecutorService(Executor executor, long maxBlockingInMillis) {
		if (executor instanceof ScheduledExecutorService) {
			return (ScheduledExecutorService) executor;
		} else {
			return new WrappedExecutorService(executorService(executor), maxBlockingInMillis);
		}
	}

	@Override
	void close();

	<T> CompletionStage<T> execute(Blocked<T> blocked);

	<T> DelayedStage<T> schedule(Producer<T> producer, long delay, TimeUnit unit);

	default <T> DelayedStage<Nothing> schedule(Task task, long delay, TimeUnit unit) {
		return schedule(task.andThen(() -> Nothing.INSTANCE), delay, unit);
	}
}
