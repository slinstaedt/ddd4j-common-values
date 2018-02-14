package org.ddd4j.infrastructure.channel.spi;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import org.ddd4j.Throwing;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Props;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revision;

public interface DataAccessFactory extends Throwing.Closeable {

	static Committed<ReadBuffer, ReadBuffer> committed(ReadBuffer key, ReadBuffer value, Revision actual, Revision next, Instant timestamp,
			Props header) {
		return new Committed<>(key.mark(), value.mark(), actual, next, timestamp, header);
	}

	@Override
	default void closeChecked() throws Exception {
		// ignore
	}

	default Map<ChannelName, Integer> knownChannelNames() {
		return Collections.emptyMap();
	}

	default <R extends Recorded<ReadBuffer, ReadBuffer>> R withBuffers(R recorded, Consumer<ReadBuffer> fn) {
		fn.accept(recorded.getKey());
		fn.accept(recorded.getValue());
		return recorded;
	}
}
