package org.ddd4j.eventstore;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.RevisionsCallback;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.schema.Schema;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Uncommitted;
import org.reactivestreams.Subscriber;

public interface EventStore {

	interface Committer<K, V> {

		Promise<CommitResult<K, V>> tryCommit(Uncommitted<K, V> attempt);
	}

	interface EventChannel<K, V> {

		Schema<V> eventSchema();

		void serializeKey(WriteBuffer buffer, K key);

		ResourceDescriptor topic();
	}

	interface Publisher<K, V> {

		void subscribe(Subscriber<Committed<K, V>> subscriber, RevisionsCallback callback);
	}

	<K, V> Committer<K, V> committer(EventChannel<K, V> channel);

	<K, V> Publisher<K, V> publisher(EventChannel<K, V> channel);

	default <K, V> void subscribe(EventChannel<K, V> channel, Subscriber<Committed<K, V>> subscriber, RevisionsCallback callback) {
		publisher(channel).subscribe(subscriber, callback);
	}

	default <K, V> Promise<CommitResult<K, V>> tryCommit(EventChannel<K, V> channel, Uncommitted<K, V> attempt) {
		return committer(channel).tryCommit(attempt);
	}
}
