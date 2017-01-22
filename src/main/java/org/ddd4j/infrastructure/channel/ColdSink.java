package org.ddd4j.infrastructure.channel;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Uncommitted;

public interface ColdSink {

	Promise<CommitResult<ReadBuffer, ReadBuffer>> tryCommit(ResourceDescriptor topic, Uncommitted<ReadBuffer, ReadBuffer> attempt);
}
