package org.ddd4j.infrastructure.channel;

import java.util.stream.IntStream;

import org.ddd4j.infrastructure.ResourceDescriptor;

public interface PartitionRebalanceListener {

	void onPartitionsAssigned(ResourceDescriptor topic, IntStream partitions);

	void onPartitionsRevoked(ResourceDescriptor topic, IntStream partitions);
}
