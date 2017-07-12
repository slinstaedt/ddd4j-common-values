package org.ddd4j.infrastructure.channel;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;

import org.ddd4j.Throwing;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.collection.Props;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public interface DataAccessFactory extends Throwing.Closeable {

	static Committed<ReadBuffer, ReadBuffer> committed(ReadBuffer key, ReadBuffer value, Revision actual, Revision next,
			ZonedDateTime timestamp, Props header) {
		return new Committed<>(key.mark(), value.mark(), actual, next, timestamp, header);
	}

	static Committed<ReadBuffer, ReadBuffer> resetBuffers(Committed<ReadBuffer, ReadBuffer> committed) {
		committed.getKey().reset();
		committed.getValue().reset();
		return committed;
	}

	@Override
	default void closeChecked() throws Exception {
		// ignore
	}

	default Map<ResourceDescriptor, Integer> knownResources() {
		return Collections.emptyMap();
	}
}