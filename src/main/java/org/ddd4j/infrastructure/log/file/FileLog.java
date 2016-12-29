package org.ddd4j.infrastructure.log.file;

import java.io.IOException;
import java.nio.channels.FileChannel;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Outcome;
import org.ddd4j.infrastructure.Result;
import org.ddd4j.infrastructure.log.Log;
import org.ddd4j.infrastructure.scheduler.ColdSource;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.Input;
import org.ddd4j.value.Type;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Committer;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Uncommitted;

public class FileLog implements Log<Input>, ColdSource<Committed<Seq<Input>>> {

	private final Scheduler scheduler;
	private FileChannel channel;
	private final Committer<Seq<Input>> committer;

	public FileLog(Scheduler scheduler, FileChannel channel) throws IOException {
		this.scheduler = Require.nonNull(scheduler);
		this.committer = scheduler.createActorDecorator(Type.of(Committer.class), new Committer<Seq<Input>>() {

			private final long currentOffset = channel.size();

			@Override
			public Outcome<CommitResult<Seq<Input>>> tryCommit(Uncommitted<Seq<Input>> attempt) {
				if (currentOffset == attempt.getExpected().asLong()) {
					return null;
				} else {
					return null;
				}
			}
		});
	}

	@Override
	public ColdSource.Connection<Committed<Seq<Input>>> open(Revision startAt, boolean completeOnEnd) throws Exception {
		return new ColdSource.Connection<Committed<Seq<Input>>>() {

			private final long offset = startAt.offset();

			@Override
			public Seq<Committed<Seq<Input>>> request(int n) throws Exception {
				// TODO Auto-generated method stub
				return null;
			}
		};
	}

	@Override
	public Result<Committed<Seq<Input>>> publisher(Revision startAt, boolean completeOnEnd) {
		return scheduler.createResult(this, startAt, completeOnEnd);
	}

	@Override
	public Outcome<CommitResult<Seq<Input>>> tryCommit(Uncommitted<Seq<Input>> attempt) {
		return committer.tryCommit(attempt);
	}
}
