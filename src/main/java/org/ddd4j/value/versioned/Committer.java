package org.ddd4j.value.versioned;

import org.ddd4j.infrastructure.Outcome;

@FunctionalInterface
public interface Committer<K, V> {

	Outcome<CommitResult<K, V>> tryCommit(Uncommitted<K, V> attempt);
}
