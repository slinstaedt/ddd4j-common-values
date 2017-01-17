package org.ddd4j.infrastructure.channel;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.buffer.ReadBuffer;
import org.ddd4j.value.versioned.Committed;

public interface HotSink {

	interface Publisher {

		void publish(Committed<ReadBuffer, ReadBuffer> committed);
	}

	default Publisher publisher(ResourceDescriptor descriptor) {
		Require.nonNull(descriptor);
		return attempt -> publish(descriptor, attempt);
	}

	void publish(ResourceDescriptor descriptor, Committed<ReadBuffer, ReadBuffer> committed);
}
