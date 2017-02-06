package org.ddd4j.eventstore;

import org.ddd4j.contract.Require;
import org.ddd4j.eventstore.Repository.Committer;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ColdSink;
import org.ddd4j.infrastructure.channel.HotSink;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Uncommitted;

public class SinkCommitter implements Committer<ReadBuffer, ReadBuffer> {

	private final ResourceDescriptor topic;
	private final ColdSink coldSink;
	private final HotSink hotSink;

	public SinkCommitter(ResourceDescriptor topic, ColdSink coldSink, HotSink hotSink) {
		this.topic = Require.nonNull(topic);
		this.coldSink = Require.nonNull(coldSink);
		this.hotSink = Require.nonNull(hotSink);
	}

	@Override
	public Promise<CommitResult<ReadBuffer, ReadBuffer>> tryCommit(Uncommitted<ReadBuffer, ReadBuffer> attempt) {
		Promise<CommitResult<ReadBuffer, ReadBuffer>> promise = coldSink.tryCommit(topic, attempt);
		return promise.whenCompleteSuccessfully(r -> r.visitCommitted(c -> hotSink.publish(topic, c)));
	}
}
