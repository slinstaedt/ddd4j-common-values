package org.ddd4j.log;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.RevisionsCallback;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.schema.Schema;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Uncommitted;
import org.reactivestreams.Subscriber;

public interface Log {

	interface Committer<K, V> {

		Promise<CommitResult<K, V>> tryCommit(Uncommitted<K, V> attempt);
	}

	// TODO rename?
	interface LogChannel<K, V> {

		Schema<V> eventSchema();

		void serializeKey(WriteBuffer buffer, K key);

		ResourceDescriptor topic();
	}

	interface Publisher<K, V> {

		void subscribe(Subscriber<Committed<K, V>> subscriber, RevisionsCallback callback);
	}

	<K, V> Committer<K, V> committer(LogChannel<K, V> channel);

	<K, V> Publisher<K, V> publisher(LogChannel<K, V> channel);

	default <K, V> void subscribe(LogChannel<K, V> channel, Subscriber<Committed<K, V>> subscriber, RevisionsCallback callback) {
		publisher(channel).subscribe(subscriber, callback);
	}

	default <K, V> Promise<CommitResult<K, V>> tryCommit(LogChannel<K, V> channel, Uncommitted<K, V> attempt) {
		return committer(channel).tryCommit(attempt);
	}
}
