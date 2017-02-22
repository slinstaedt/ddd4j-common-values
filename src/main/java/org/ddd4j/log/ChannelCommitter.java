package org.ddd4j.log;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ColdChannel;
import org.ddd4j.infrastructure.channel.HotChannel;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.log.Log.Committer;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Uncommitted;

public class ChannelCommitter implements Committer<ReadBuffer, ReadBuffer> {

	private final ResourceDescriptor topic;
	private final ColdChannel coldChannel;
	private final HotChannel hotChannel;

	public ChannelCommitter(ResourceDescriptor topic, ColdChannel coldChannel, HotChannel hotChannel) {
		this.topic = Require.nonNull(topic);
		this.coldChannel = Require.nonNull(coldChannel);
		this.hotChannel = Require.nonNull(hotChannel);
	}

	@Override
	public Promise<CommitResult<ReadBuffer, ReadBuffer>> tryCommit(Uncommitted<ReadBuffer, ReadBuffer> attempt) {
		Promise<CommitResult<ReadBuffer, ReadBuffer>> promise = coldChannel.trySend(topic, attempt);
		return promise.whenCompleteSuccessfully(r -> r.visitCommitted(c -> hotChannel.send(topic, c)));
	}
}
