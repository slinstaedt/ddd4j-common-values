package org.ddd4j.repository.api;

import java.util.function.Function;

import org.ddd4j.Throwing;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revisions;
import org.ddd4j.value.versioned.Uncommitted;

public interface Writer<K, V> extends Committer<K, V> {

	interface Factory extends Throwing.Closeable {

		Key<Factory> KEY = Key.of(Factory.class);

		Writer<ReadBuffer, ReadBuffer> create(ResourceDescriptor descriptor);
	}

	default Promise<Committed<K, V>> put(K key, V value) {
		return put(Recorded.uncommitted(key, value, Revisions.NONE));
	}

	Promise<Committed<K, V>> put(Recorded<K, V> attempt);

	@Override
	default Promise<? extends CommitResult<K, V>> commit(Uncommitted<K, V> attempt) {
		return put(attempt);
	}

	@Override
	default <X, Y> Writer<X, Y> map(Function<? super X, K> key, Function<? super Y, V> value) {
		return r -> put(r.map(key, value)).thenApply(c -> c.with(r.getKey(), r.getValue()));
	}
}