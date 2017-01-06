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
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
import org.ddd4j.value.versioned.Revisions;
import org.ddd4j.value.versioned.Uncommitted;

public class FileLog implements Log<ReadBuffer>, ColdSource<Committed<Seq<ReadBuffer>>> {

	private class FileConnection implements Connection<Committed<Seq<ReadBuffer>>> {

		private final boolean completeOnEnd;

		FileConnection(boolean completeOnEnd) {
			this.completeOnEnd = completeOnEnd;
		}

		@Override
		public void closeChecked() throws Exception {
		}

		private Committed<Seq<ReadBuffer>> readCommitted(long position, ByteBuffer buffer) {
			Revisions actual = new Revisions(position + buffer.position() - Integer.BYTES, partition);
			Identifier identifier = new Identifier(buffer.getLong(), buffer.getLong());
			LocalDateTime timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(buffer.getLong()), ZoneOffset.UTC);
			List<ReadBuffer> entries = new ArrayList<>();
			int entrySize;
			while ((entrySize = buffer.getInt()) != COMMIT_DELIM) {
				entries.add(Bytes.wrap(buffer).buffered().limit(entrySize));
			}
			Revisions expected = new Revisions(position + buffer.position() - Integer.BYTES, partition);
			buffer.position(buffer.position() - Integer.BYTES);
			return new Committed<>(identifier, entries::stream, actual, expected, timestamp);
		}

		@Override
		public Seq<Committed<Seq<ReadBuffer>>> request(Revision position, int n) throws Exception {
			ByteBuffer buffer = channel.map(MapMode.READ_ONLY, position.getOffset(), Integer.MAX_VALUE);
			List<Committed<Seq<ReadBuffer>>> result = new ArrayList<>(n);
			for (int i = 0; i < n && buffer.hasRemaining(); i++) {
				if (buffer.getInt() != COMMIT_DELIM) {
					throw new IllegalStateException("Illegal position: " + position);
				} else {
					result.add(readCommitted(position, buffer));
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
	private int partition;
	private AtomicLong currentOffset;

	public FileLog(Scheduler scheduler, Pool<ByteBuffer> bufferPool, Path file) throws IOException {
		this.scheduler = Require.nonNull(scheduler);
		this.bufferPool = Require.nonNull(bufferPool);
		this.channel = FileChannel.open(file, OPEN_OPTIONS);
	}

	@Override
	public void closeChecked() throws Exception {
		channel.close();
	}

	@Override
	public Revisions currentRevisions() throws Exception {
		return Revisions.initial(1).next(0, currentOffset.get());
	}

	@Override
	public Cursor<Committed<Seq<ReadBuffer>>> open(boolean completeOnEnd) throws Exception {
		return new FileConnection(completeOnEnd).toCursor();
	}

	@Override
	public Result<Committed<Seq<ReadBuffer>>> publisher(Revisions startAt, boolean completeOnEnd) {
		return scheduler.createResult(this, startAt, completeOnEnd);
	}

	@Override
	public Outcome<CommitResult<Seq<ReadBuffer>>> tryCommit(Uncommitted<Seq<ReadBuffer>> attempt) {
		try {
			Revisions actual = new Revisions(channel.size(), partition);
			if (!attempt.getExpected().equal(actual)) {
				return scheduler.completedOutcome(attempt.conflictsWith(actual));
			}
			channel.position(actual.asLong());
			attempt.getEntry().forEachThrowing(b -> b.writeTo(channel));
		} catch (IOException e) {
			return scheduler.failedOutcome(e);
		}
	}
}
