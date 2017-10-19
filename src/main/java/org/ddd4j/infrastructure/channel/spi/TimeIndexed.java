package org.ddd4j.infrastructure.channel.spi;

import java.time.Duration;
import java.time.Instant;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.domain.value.ChannelPartition;
import org.ddd4j.infrastructure.domain.value.ChannelRevision;

public interface TimeIndexed {

	enum Direction {
		AFTER, BEFORE;

		public boolean check(Instant reference, long millis) {
			if (this == AFTER) {
				return reference.toEpochMilli() <= millis;
			} else {
				return reference.toEpochMilli() >= millis;
			}
		}

		public Instant apply(Instant timestamp, Duration duration) {
			if (this == AFTER) {
				return timestamp.plus(duration);
			} else {
				return timestamp.minus(duration);
			}
		}
	}

	Promise<ChannelRevision> revision(ChannelPartition partition, Instant timestamp, Direction direction);
}
