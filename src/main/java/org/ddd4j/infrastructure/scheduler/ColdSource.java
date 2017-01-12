package org.ddd4j.infrastructure.scheduler;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Throwing.Closeable;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Revision;

public interface ColdSource<T> {

	interface Connection<T> extends Closeable {

		Seq<T> request(Revision position, int n) throws Exception;

		default Cursor<T> toCursor() {
			return new StatelessConnectionWrapper<>(this);
		}
	}

	interface Cursor<T> extends Closeable {

		void position(Revision position) throws Exception;

		Seq<? extends T> requestNext(int n) throws Exception;
	}

	class StatelessConnectionWrapper<T> implements Cursor<T> {

		private final Connection<T> delegate;
		private Revision position;

		public StatelessConnectionWrapper(Connection<T> delegate) {
			this.delegate = Require.nonNull(delegate);
			this.position = 0;
		}

		@Override
		public void closeChecked() throws Exception {
			delegate.close();
		}

		@Override
		public void position(Revision position) throws Exception {
			this.position = Require.nonNull(position);
		}

		@Override
		public Seq<T> requestNext(int n) throws Exception {
			Seq<T> result = delegate.request(position, n);
			position = position.increment(result.checkFinite().sizeIfKnown());
			return result;
		}
	}

	Cursor<T> open() throws Exception;
}