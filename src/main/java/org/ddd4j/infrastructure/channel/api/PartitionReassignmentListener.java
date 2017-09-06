package org.ddd4j.infrastructure.channel.api;

import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.channel.domain.ChannelPartition;

public interface PartitionReassignmentListener {

	void onPartitionsAssigned(Sequence<ChannelPartition> partitions);

	void onPartitionsRevoked(Sequence<ChannelPartition> partitions);
}
