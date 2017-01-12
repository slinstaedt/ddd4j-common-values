package org.ddd4j.infrastructure.pipe;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.Outcome;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.buffer.Bytes;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Uncommitted;

public interface Sink {

	interface Committer {

		Outcome<CommitResult<Bytes, Bytes>> tryCommit(Uncommitted<Bytes, Bytes> attempt);
	}

	default Committer createCommitter(ResourceDescriptor descriptor) {
		Require.nonNull(descriptor);
		return attempt -> tryCommit(descriptor, attempt);
	}

	Outcome<CommitResult<Bytes, Bytes>> tryCommit(ResourceDescriptor descriptor, Uncommitted<Bytes, Bytes> attempt);
}
