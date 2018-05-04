package org.ddd4j.infrastructure.channel.spi;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import org.ddd4j.infrastructure.domain.header.Headers;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Throwing;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public interface DataAccessFactory extends Throwing.Closeable {

	static Committed<ReadBuffer, ReadBuffer> committed(ReadBuffer key, ReadBuffer value, Revision actual, Revision next, Instant timestamp,
			Map<String, ReadBuffer> headers) {
		return new Committed<>(key.mark(), value.mark(), actual, next, timestamp, new Headers(headers));
	}

	@Override
	default void closeChecked() throws Exception {
		// ignore
	}

	default Map<ChannelName, Integer> knownChannelNames() {
		return Collections.emptyMap();
	}
}
