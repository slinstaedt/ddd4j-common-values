package org.ddd4j.infrastructure.channel.api;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.domain.value.ChannelPartition;
import org.ddd4j.util.Sequence;

@FunctionalInterface
public interface RebalanceListener {

	enum Mode {
		ASSIGNED, REVOKED;
	}

	RebalanceListener VOID = (m, p) -> Promise.completed();

	Promise<?> onRebalance(Mode mode, Sequence<ChannelPartition> partitions);
}
