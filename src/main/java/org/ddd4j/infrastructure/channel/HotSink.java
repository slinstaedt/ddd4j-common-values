package org.ddd4j.infrastructure.channel;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;

public interface HotSink {

	void publish(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed);
}
