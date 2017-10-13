package org.ddd4j.infrastructure.channel.api;

import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.domain.value.ChannelPartition;

public interface RepartitioningListener {

	@FunctionalInterface
	interface Mapper<C> {

		Promise<?> apply(C callback, Sequence<ChannelPartition> partitions);
	}

	RepartitioningListener VOID = new RepartitioningListener() {

		@Override
		public Promise<?> onPartitionsAssigned(Sequence<ChannelPartition> partitions) {
			return Promise.completed();
		}

		@Override
		public Promise<?> onPartitionsRevoked(Sequence<ChannelPartition> partitions) {
			return Promise.completed();
		}
	};

	Promise<?> onPartitionsAssigned(Sequence<ChannelPartition> partitions);

	Promise<?> onPartitionsRevoked(Sequence<ChannelPartition> partitions);
}
