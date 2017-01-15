package org.ddd4j.infrastructure.pipe;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Outcome;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.buffer.ReadBuffer;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Uncommitted;

public interface ColdSink {

	interface Committer {

		Outcome<CommitResult<ReadBuffer, ReadBuffer>> tryCommit(Uncommitted<ReadBuffer, ReadBuffer> attempt);
	}

	default Committer committer(ResourceDescriptor descriptor) {
		Require.nonNull(descriptor);
		return attempt -> tryCommit(descriptor, attempt);
	}

	Outcome<CommitResult<ReadBuffer, ReadBuffer>> tryCommit(ResourceDescriptor descriptor, Uncommitted<ReadBuffer, ReadBuffer> attempt);
}
