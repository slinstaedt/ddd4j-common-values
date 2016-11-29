package org.ddd4j.infrastructure.queue.disruptor;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.queue.Queue;
import org.ddd4j.value.Throwing;

import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.RingBuffer;

public class DisruptorQueue<E> implements Queue<E> {

	class DisruptorConsumer implements Consumer<E>, EventPoller.Handler<DisruptorEvent<E>> {

		private final EventPoller<DisruptorEvent<E>> poller;
		private E value;

		DisruptorConsumer() {
			this.poller = buffer.newPoller();
		}

		@Override
		public E next() {
			try {
				switch (poller.poll(this)) {
				case PROCESSING:
					return value;
				default:
					return null;
				}
			} catch (Exception e) {
				return Throwing.unchecked(e);
			}
		}

		@Override
		public boolean onEvent(DisruptorEvent<E> event, long sequence, boolean endOfBatch) {
			this.value = Require.nonNull(event.value);
			return false;
		}
	}

	private static class DisruptorEvent<E> {

		private E value;
	}

	class DisruptorTransaction implements Transaction<E> {

		private final long seqLo;
		private final long seqHi;
		private long current;

		DisruptorTransaction(int n) {
			seqHi = buffer.next(n);
			seqLo = seqHi - n + 1;
			current = seqLo;
		}

		@Override
		public void add(E value) {
			Require.that(current < seqHi);
			buffer.get(current++).value = Require.nonNull(value);
		}

		@Override
		public void close() {
			buffer.publish(seqLo, seqHi);
		}
	}

	private final RingBuffer<DisruptorEvent<E>> buffer;

	public DisruptorQueue(int bufferSize) {
		this.buffer = RingBuffer.createMultiProducer(DisruptorEvent::new, bufferSize);
	}

	@Override
	public Consumer<E> consumer() {
		return new DisruptorConsumer();
	}

	@Override
	public Producer<E> producer() {
		return DisruptorTransaction::new;
	}
}
