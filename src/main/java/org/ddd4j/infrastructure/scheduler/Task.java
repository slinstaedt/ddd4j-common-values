package org.ddd4j.infrastructure.scheduler;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing.TRunnable;
import org.ddd4j.value.Value;

@FunctionalInterface
public interface Task extends TRunnable {

	interface Dispatcher {

		void dispatch(QueueIdentifier identifier, Task task);
	}

	class Queue {

		private final java.util.Queue<Task> tasks;
		private final AtomicBoolean scheduled;

		public Queue() {
			this.tasks = new ConcurrentLinkedQueue<>();
			this.scheduled = new AtomicBoolean(false);
		}

		public void add(Task task) {
			tasks.offer(Require.nonNull(task));
		}

		public void flagUnscheduled() {
			scheduled.set(false);
		}

		public boolean needsToBeScheduled() {
			return !scheduled.getAndSet(true);
		}

		public Task poll() {
			return tasks.poll();
		}
	}

	class QueueIdentifier extends Value.Simple<QueueIdentifier, UUID> {

		private final UUID value;

		public QueueIdentifier() {
			this.value = UUID.randomUUID();
		}

		@Override
		protected UUID value() {
			return value;
		}
	}
}