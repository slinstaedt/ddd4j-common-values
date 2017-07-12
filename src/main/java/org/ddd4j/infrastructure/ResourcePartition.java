package org.ddd4j.infrastructure;

import org.ddd4j.Require;
import org.ddd4j.value.Value;

public class ResourcePartition implements Value<ResourcePartition> {

	private final ResourceDescriptor resource;
	private final int partition;

	public ResourcePartition(ResourceDescriptor resource, int partition) {
		this.resource = Require.nonNull(resource);
		this.partition = Require.that(partition, partition >= 0);
	}

	public int getPartition() {
		return partition;
	}

	public ResourceDescriptor getResource() {
		return resource;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + partition;
		result = prime * result + resource.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		} else if (obj == null) {
			return false;
		} else if (getClass() != obj.getClass()) {
			return false;
		}
		ResourcePartition other = (ResourcePartition) obj;
		return partition == other.partition && resource.equals(other.resource);
	}
}
