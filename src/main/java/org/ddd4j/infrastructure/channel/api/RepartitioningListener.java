package org.ddd4j.infrastructure.channel.api;

import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.domain.value.ChannelPartition;

public interface RepartitioningListener {

	RepartitioningListener VOID = new RepartitioningListener() {
	};

	default Promise<?> onPartitionsRevoked(Sequence<ChannelPartition> partitions) {
		return Promise.completed();
	}

	default Promise<?> onPartitionsAssigned(Sequence<ChannelPartition> partitions) {
		return Promise.completed();
	}
}
