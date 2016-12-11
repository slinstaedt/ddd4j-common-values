package org.ddd4j.infrastructure.queue;

import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ddd4j.contract.Require;

public class ScheduledQueue<E> implements Queue<E> {

	private class ScheduledBatch implements Batch<E> {

		private final Batch<E> delegate;

		ScheduledBatch(Batch<E> delegate) {
			this.delegate = Require.nonNull(delegate);
		}

		@Override
		public void add(E value) {
			delegate.add(value);
		}

		@Override
		public void closeChecked() throws Exception {
			delegate.closeChecked();
		}
	}

	private class ScheduledConsumer implements Consumer<E> {

		private final Consumer<E> delegate;
		private final AtomicBoolean scheduled;

		ScheduledConsumer(Consumer<E> delegate) {
			this.delegate = Require.nonNull(delegate);
			this.scheduled = new AtomicBoolean(false);
		}

		@Override
		public void closeChecked() throws Exception {
			delegate.closeChecked();
		}

		@Override
		public E next() {
			return delegate.next();
		}

		void scheduleIfNeeded(Executor executor) {
			if (scheduled.compareAndSet(false, true)) {
				executor.execute(createAsyncConsumer(t -> t.executeWith(getState()), null, null, null));
			}
		}
	}

	private class ScheduledProducer implements Producer<E> {

		private final Producer<E> delegate;

		public ScheduledProducer(Producer<E> delegate) {
			this.delegate = delegate;
		}

		@Override
		public void closeChecked() throws Exception {
			delegate.closeChecked();
		}

		@Override
		public Batch<E> nextBatch(int n) {
			return new ScheduledBatch(delegate.nextBatch(n));
		}
	}

	private Queue<E> delegate;
	private Executor executor;
	private Set<ScheduledConsumer> consumers;

	@Override
	public Consumer<E> consumer() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getBufferSize() {
		return delegate.getBufferSize();
	}

	@Override
	public Producer<E> producer() {
		return null;
	}
}
