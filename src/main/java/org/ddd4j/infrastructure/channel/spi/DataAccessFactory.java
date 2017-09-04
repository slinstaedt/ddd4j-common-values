package org.ddd4j.infrastructure.channel.spi;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;

import org.ddd4j.Throwing;
import org.ddd4j.collection.Props;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public interface DataAccessFactory extends Throwing.Closeable {

	static Committed<ReadBuffer, ReadBuffer> committed(ReadBuffer key, ReadBuffer value, Revision actual, Revision next,
			OffsetDateTime timestamp, Props header) {
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

	default Map<ChannelName, Integer> knownChannelNames() {
		return Collections.emptyMap();
	}
}
