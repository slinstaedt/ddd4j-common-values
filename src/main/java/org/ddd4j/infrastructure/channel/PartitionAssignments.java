package org.ddd4j.infrastructure.channel;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.ResourceDescriptor;

public class PartitionAssignments {

	private final ResourceDescriptor topic;
	private final AtomicBoolean[] partitions;

	public PartitionAssignments(ResourceDescriptor topic, int partitionSize) {
		this.topic = Require.nonNull(topic);
		this.partitions = new AtomicBoolean[partitionSize];
		for (int i = 0; i < partitions.length; i++) {
			partitions[i] = new AtomicBoolean(false);
		}
	}

	public PartitionAssignments(String topic, int partitionSize) {
		this(ResourceDescriptor.of(topic), partitionSize);
	}

	public IntStream assigned() {
		return IntStream.range(0, partitions.length).filter(p -> partitions[p].get());
	}

	public boolean assigned(int partition) {
		return partitions[partition].compareAndSet(false, true);
	}

	public ResourceDescriptor getTopic() {
		return topic;
	}

	public int partitionSize() {
		return partitions.length;
	}

	public boolean unassigned(int partition) {
		return partitions[partition].compareAndSet(true, false);
	}
}