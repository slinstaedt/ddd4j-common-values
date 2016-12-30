package org.ddd4j.infrastructure.log.file;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.DSYNC;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.channels.FileLock;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.ddd4j.aggregate.Identifier;
import org.ddd4j.collection.Cache.Pool;
import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Outcome;
import org.ddd4j.infrastructure.Result;
import org.ddd4j.infrastructure.log.Log;
import org.ddd4j.infrastructure.scheduler.ColdSource;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.buffer.Bytes;
import org.ddd4j.io.buffer.ReadBuffer;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Uncommitted;

public class FileLog implements Log<ReadBuffer>, ColdSource<Committed<Seq<ReadBuffer>>> {

	private class FileConnection implements StatelessConnection<Committed<Seq<ReadBuffer>>> {

		private final boolean completeOnEnd;

		FileConnection(boolean completeOnEnd) {
			this.completeOnEnd = completeOnEnd;
		}

		@Override
		public void close() throws Exception {
		}

		private Committed<Seq<ReadBuffer>> readCommitted(ByteBuffer buffer) {
			Identifier identifier = new Identifier(buffer.getLong(), buffer.getLong());
			Revision actual = new Revision(buffer.getLong());
			Revision expected = new Revision(buffer.getLong());
			LocalDateTime timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(buffer.getLong()), ZoneOffset.UTC);
			List<ReadBuffer> entries = new ArrayList<>();
			int entrySize;
			while ((entrySize = buffer.getInt()) != COMMIT_DELIM) {
				entries.add(Bytes.wrap(buffer).buffered().limit(entrySize));
			}
			buffer.position(buffer.position() - Integer.BYTES);
			return new Committed<>(identifier, entries::stream, actual, expected, timestamp);
		}

		@Override
		public Seq<Committed<Seq<ReadBuffer>>> request(long position, int n) throws Exception {
			ByteBuffer buffer = channel.map(MapMode.READ_ONLY, position, channel.size());
			List<Committed<Seq<ReadBuffer>>> result = new ArrayList<>(n);
			for (int i = 0; i < n && buffer.hasRemaining(); i++) {
				if (buffer.getInt() != COMMIT_DELIM) {
					throw new IllegalStateException("Illegal position: " + position);
				} else {
					result.add(readCommitted(buffer));
				}
			}
			return result::stream;
		}
	}

	private static final OpenOption[] OPEN_OPTIONS = { CREATE, READ, WRITE, APPEND, DSYNC };
	private static final int COMMIT_DELIM = 0xFFFFAFFE;

	private final Scheduler scheduler;
	private final Pool<ByteBuffer> bufferPool;
	private final FileChannel channel;

	public FileLog(Scheduler scheduler, Pool<ByteBuffer> bufferPool, Path file) throws IOException {
		this.scheduler = Require.nonNull(scheduler);
		this.bufferPool = Require.nonNull(bufferPool);
		this.channel = FileChannel.open(file, OPEN_OPTIONS);
	}

	@Override
	public void close() throws IOException {
		channel.close();
	}

	@Override
	public Connection<Committed<Seq<ReadBuffer>>> open(boolean completeOnEnd) throws Exception {
		return new FileConnection(completeOnEnd).toStatefulConnection();
	}

	@Override
	public Result<Committed<Seq<ReadBuffer>>> publisher(Revision startAt, boolean completeOnEnd) {
		return scheduler.createResult(this, startAt, completeOnEnd);
	}

	@Override
	public Outcome<CommitResult<Seq<ReadBuffer>>> tryCommit(Uncommitted<Seq<ReadBuffer>> attempt) {
		try {
			Revision actual = new Revision(channel.size());
			if (!attempt.getExpected().equal(actual)) {
				return scheduler.completedOutcome(attempt.conflictsWith(actual));
			}
			try (FileLock lock = channel.tryLock()) {
				if (lock == null) {
					return scheduler.completedOutcome(attempt.conflictsWith(new Revision(channel.size())));
				}
				channel.position(actual.asLong());
				attempt.getEntry().forEachThrowing(b -> b.writeTo(channel));
			}
		} catch (IOException e) {
			return scheduler.failedOutcome(e);
		}
	}
}
