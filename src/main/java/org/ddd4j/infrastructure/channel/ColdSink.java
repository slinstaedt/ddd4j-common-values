package org.ddd4j.infrastructure.channel;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.buffer.ReadBuffer;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Uncommitted;

public interface ColdSink {

	interface Committer {

		Promise<CommitResult<ReadBuffer, ReadBuffer>> tryCommit(Uncommitted<ReadBuffer, ReadBuffer> attempt);
	}

	default Committer committer(ResourceDescriptor descriptor) {
		Require.nonNull(descriptor);
		return attempt -> tryCommit(descriptor, attempt);
	}

	Promise<CommitResult<ReadBuffer, ReadBuffer>> tryCommit(ResourceDescriptor descriptor, Uncommitted<ReadBuffer, ReadBuffer> attempt);
}
