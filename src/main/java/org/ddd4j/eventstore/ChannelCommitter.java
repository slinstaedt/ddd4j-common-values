package org.ddd4j.eventstore;

import org.ddd4j.contract.Require;
import org.ddd4j.eventstore.Repository.Committer;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ColdChannel;
import org.ddd4j.infrastructure.channel.HotChannel;
import org.ddd4j.io.ReadBuffer;
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
		Promise<CommitResult<ReadBuffer, ReadBuffer>> promise = coldChannel.tryCommit(topic, attempt);
		return promise.whenCompleteSuccessfully(r -> r.visitCommitted(c -> hotChannel.publish(topic, c)));
	}
}
