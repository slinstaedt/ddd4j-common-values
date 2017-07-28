package org.ddd4j.infrastructure;

import org.ddd4j.Require;
import org.ddd4j.value.Value;
import org.ddd4j.value.versioned.Revision;

public class ResourceRevision extends Value.Comlex<ResourceRevision> {

	private final ResourceDescriptor resource;
	private final Revision revision;

	public ResourceRevision(ResourceDescriptor resource, Revision revision) {
		this.resource = Require.nonNull(resource);
		this.revision = Require.nonNull(revision);
	}

	public ResourceRevision(String resource, int partition, long offset) {
		this(ResourceDescriptor.of(resource), new Revision(partition, offset));
	}

	public long getOffset() {
		return revision.getOffset();
	}

	public int getPartition() {
		return revision.getPartition();
	}

	public ResourceDescriptor getResource() {
		return resource;
	}

	public Revision getRevision() {
		return revision;
	}

	@Override
	protected Value<?>[] value() {
		return new Value<?>[] { resource, revision };
	}
}
