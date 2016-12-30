package org.ddd4j.infrastructure.scheduler;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.collection.Seq;

public interface ColdSource<T> {

	interface Connection<T> extends AutoCloseable {

		default void closeUnchecked() {
			try {
				close();
			} catch (Exception e) {
				Throwing.unchecked(e);
			}
		}

		void position(long position) throws Exception;

		Seq<? extends T> requestNext(int n) throws Exception;
	}

	interface StatelessConnection<T> extends AutoCloseable {

		Seq<T> request(long position, int n) throws Exception;

		default Connection<T> toStatefulConnection() {
			return new StatelessConnectionWrapper<>(this);
		}
	}

	class StatelessConnectionWrapper<T> implements Connection<T> {

		private final StatelessConnection<T> delegate;
		private long position;

		public StatelessConnectionWrapper(StatelessConnection<T> delegate) {
			this.delegate = Require.nonNull(delegate);
			this.position = 0;
		}

		@Override
		public void close() throws Exception {
			delegate.close();
		}

		@Override
		public void position(long position) throws Exception {
			this.position = position;
		}

		@Override
		public Seq<T> requestNext(int n) throws Exception {
			Seq<T> result = delegate.request(position, n);
			position += result.checkFinite().sizeIfKnown();
			return result;
		}
	}

	Connection<T> open(boolean completeOnEnd) throws Exception;
}