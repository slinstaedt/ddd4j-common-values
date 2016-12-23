package org.ddd4j.infrastructure.log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;

import org.ddd4j.value.Value;
import org.reactivestreams.Publisher;

public interface Log {

	class Commit implements Value<Commit> {

		private ByteBuffer value;
		private Offset expected;
		private Long timestamp;
	}

	class Offset extends Value.Simple<Offset, Long> {

		public static final Offset START = new Offset(0);
		public static final Offset LATEST = new Offset(-1);

		private final long value;

		public Offset(long value) {
			this.value = value;
		}

		public long getValue() {
			return value;
		}

		public boolean isEnd() {
			return value == -1;
		}

		@Override
		protected Long value() {
			return value;
		}
	}

	class Record implements Value<Record> {

		private ByteBuffer value;
		private Offset committed;
		private Offset nextExpected;
		private long timestamp;
	}

	Publisher<Record> publisher(Offset initialOffset, boolean completeOnEnd) throws IOException;

	default Publisher<Record> readFrom(Offset initialOffset) throws IOException {
		return publisher(initialOffset, true);
	}

	default Publisher<Record> registerListener(Offset initialOffset) throws IOException {
		return publisher(initialOffset, false);
	}

	CompletionStage<Record> tryAppend(Commit commit) throws IOException;
}
