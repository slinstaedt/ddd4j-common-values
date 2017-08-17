package org.ddd4j.infrastructure.channel.old;

import org.ddd4j.Throwing.Closeable;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;

public interface HotChannel extends Closeable {

	interface Callback {

		Promise<Integer> subscribe(ChannelName topic);

		void unsubscribe(ChannelName topic);
	}

	interface Listener extends ChannelListener, PartitionRebalanceListener {
	}

	void send(ChannelName topic, Committed<ReadBuffer, ReadBuffer> committed);

	Callback register(Listener listener);
}
