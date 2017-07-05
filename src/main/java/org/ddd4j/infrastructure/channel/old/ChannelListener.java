package org.ddd4j.infrastructure.channel.old;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;

public interface ChannelListener {

	void onError(Throwable throwable);

	void onNext(ResourceDescriptor topic, Committed<ReadBuffer, ReadBuffer> committed);
}
