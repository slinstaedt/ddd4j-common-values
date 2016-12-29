package org.ddd4j.value.versioned;

import org.ddd4j.infrastructure.Outcome;

@FunctionalInterface
public interface Committer<E> {

	Outcome<CommitResult<E>> tryCommit(Uncommitted<E> attempt);
}
