package org.ddd4j.infrastructure.channel.old;

import org.ddd4j.Throwing.Closeable;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Uncommitted;

public interface ColdChannel extends Closeable {

	interface Callback {

		void seek(ChannelName topic, Revision revision);

		void unseek(ChannelName topic, int partition);

		// void pause(ChannelName topic);
		// void unpause(ChannelName topic);
	}

	interface Listener extends ChannelListener {
	}

	Promise<CommitResult<ReadBuffer, ReadBuffer>> trySend(ChannelName topic, Uncommitted<ReadBuffer, ReadBuffer> attempt);

	Callback register(Listener listener);
}
