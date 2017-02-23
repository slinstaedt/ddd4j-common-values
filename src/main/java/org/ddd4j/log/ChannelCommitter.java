package org.ddd4j.log;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.Channel;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.log.Log.Committer;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Uncommitted;

public class ChannelCommitter implements Committer<ReadBuffer, ReadBuffer> {

	private final ResourceDescriptor topic;
	private final Channel channel;

	public ChannelCommitter(ResourceDescriptor topic, Channel channel) {
		this.topic = Require.nonNull(topic);
		this.channel = Require.nonNull(channel);
	}

	@Override
	public Promise<CommitResult<ReadBuffer, ReadBuffer>> tryCommit(Uncommitted<ReadBuffer, ReadBuffer> attempt) {
		Promise<CommitResult<ReadBuffer, ReadBuffer>> promise = channel.trySend(topic, attempt);
		return promise.whenCompleteSuccessfully(r -> r.visitCommitted(c -> channel.send(topic, c)));
	}
}
