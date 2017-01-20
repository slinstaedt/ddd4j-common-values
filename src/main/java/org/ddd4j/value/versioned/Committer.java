package org.ddd4j.value.versioned;

import org.ddd4j.infrastructure.Promise;

@FunctionalInterface
public interface Committer<K, V> {

	Promise<CommitResult<K, V>> tryCommit(Uncommitted<K, V> attempt);
}
