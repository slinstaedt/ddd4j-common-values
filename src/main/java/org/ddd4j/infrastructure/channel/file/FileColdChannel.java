package org.ddd4j.infrastructure.channel.file;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.DSYNC;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.ddd4j.aggregate.Identifier;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ChannelListener;
import org.ddd4j.infrastructure.channel.ColdChannel;
import org.ddd4j.infrastructure.scheduler.Agent;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.Bytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;
import org.ddd4j.value.versioned.Uncommitted;

public class FileColdChannel implements ColdChannel {

	private class FileConnection {

		private Committed<ReadBuffer, Seq<ReadBuffer>> readCommitted(long position, ByteBuffer buffer) {
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

		public Seq<Committed<ReadBuffer, Seq<ReadBuffer>>> request(Revision position, int n) throws Exception {
			ByteBuffer buffer = channel.map(MapMode.READ_ONLY, position.getOffset(), Integer.MAX_VALUE);
			List<Committed<ReadBuffer, Seq<ReadBuffer>>> result = new ArrayList<>(n);
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

	private class Committer implements Closeable {

		CommitResult<ReadBuffer, ReadBuffer> tryCommit(ResourceDescriptor topic, Uncommitted<ReadBuffer, ReadBuffer> attempt) {

		}

		@Override
		public void close() throws IOException {
			// TODO Auto-generated method stub

		}
	}

	private static final OpenOption[] OPEN_OPTIONS = { CREATE, READ, WRITE, APPEND, DSYNC };
	private static final int COMMIT_DELIM = 0xFFFFAFFE;

	private Scheduler scheduler;
	private Path baseDirectory;
	private int partitionsPerTopic;
	private Agent<Committer> committer;

	@Override
	public void closeChecked() throws Exception {
		committer.perform(Committer::close).toCompletionStage().toCompletableFuture().join();
	}

	@Override
	public void register(ChannelListener listener) {
		FileColdCallback callback = new FileColdCallback();
		listener.onColdRegistration(callback);
	}

	@Override
	public Promise<CommitResult<ReadBuffer, ReadBuffer>> trySend(ResourceDescriptor topic, Uncommitted<ReadBuffer, ReadBuffer> attempt) {
		return committer.execute(c -> c.tryCommit(topic, attempt));
	}
}
