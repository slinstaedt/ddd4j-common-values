package org.ddd4j.infrastructure.scheduler;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ddd4j.contract.Require;

public class TaskQueue {

	private final Queue<Task> tasks;
	private final AtomicBoolean scheduled;

	public TaskQueue() {
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
