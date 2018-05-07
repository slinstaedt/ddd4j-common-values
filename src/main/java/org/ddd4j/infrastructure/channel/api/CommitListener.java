package org.ddd4j.infrastructure.channel.api;

import java.util.function.BiFunction;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

//TODO rename to RecordListener
@FunctionalInterface
public interface CommitListener<K, V> {

	CommitListener<ReadBuffer, ReadBuffer> VOID = (n, c) -> Promise.completed();

	default <X, Y> CommitListener<X, Y> mapPromised(BiFunction<? super X, Revision, Promise<K>> key,
			BiFunction<? super Y, Revision, Promise<V>> value, ErrorListener error) {
		Require.nonNulls(key, value, error);
		return (name, cxy) -> key.apply(cxy.getKey(), cxy.getActual())
				.thenCombine(value.apply(cxy.getValue(), cxy.getActual()), cxy::withKeyValue)
				.thenCompose(ckv -> onNext(name, ckv))
				.whenCompleteExceptionally(error::onError);
	}

	Promise<?> onNext(ChannelName name, Committed<K, V> committed);
}