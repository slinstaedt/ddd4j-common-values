package org.ddd4j.infrastructure.channel;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.Throwing.Closeable;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Uncommitted;

public interface ColdChannel extends Closeable {

	interface Callback extends Closeable {

		void seek(ResourceDescriptor topic, Revision revision);

		void unseek(ResourceDescriptor topic, int partition);

		// void pause(ResourceDescriptor topic);
		// void unpause(ResourceDescriptor topic);
	}

	interface Listener extends ChannelListener {
	}

	Promise<CommitResult<ReadBuffer, ReadBuffer>> tryCommit(ResourceDescriptor topic, Uncommitted<ReadBuffer, ReadBuffer> attempt);

	Callback register(Listener listener);
}
