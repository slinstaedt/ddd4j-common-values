package org.ddd4j.infrastructure.channel;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.Throwing.Closeable;
import org.ddd4j.value.versioned.Committed;

public interface HotChannel extends Closeable {

	void publish(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed);

	void register(ChannelListener listener);
}
