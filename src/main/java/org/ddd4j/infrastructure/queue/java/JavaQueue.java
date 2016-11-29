package org.ddd4j.infrastructure.queue.java;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.queue.Queue;

public class JavaQueue<E> implements Queue<E> {

	private class JavaConsumer implements Consumer<E> {

		private final java.util.Queue<E> delegate;

		JavaConsumer() {
			this.delegate = new ConcurrentLinkedQueue<>();
		}

		@Override
		public void close() {
			consumers.remove(this);
		}

		@Override
		public E next() {
			return delegate.poll();
		}

		void publish(List<E> values) {
			delegate.addAll(values);
		}
	}

	private class JavaTransaction implements Transaction<E> {

		private final List<E> values;

		JavaTransaction(int capacity) {
			this.values = new ArrayList<>(capacity);
		}

		@Override
		public void add(E value) {
			values.add(Require.nonNull(value));
		}

		@Override
		public void close() {
			consumers.forEach(c -> c.publish(values));
		}
	}

	private final List<JavaConsumer> consumers;

	public JavaQueue() {
		this.consumers = new CopyOnWriteArrayList<>();
	}

	@Override
	public Consumer<E> consumer() {
		JavaConsumer consumer = new JavaConsumer();
		consumers.add(consumer);
		return consumer;
	}

	@Override
	public Producer<E> producer() {
		return JavaTransaction::new;
	}
}