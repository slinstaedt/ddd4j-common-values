package org.ddd4j.infrastructure.channel.api;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

//TODO rename to RecordListener
@FunctionalInterface
public interface CommitListener<K, V> {

	CommitListener<ReadBuffer, ReadBuffer> VOID = (n, c) -> Promise.completed(c);

	default <X, Y> CommitListener<X, Y> mapPromised(Function<? super X, K> key, BiFunction<? super Y, Revision, Promise<V>> value,
			ErrorListener error) {
		Require.nonNulls(key, value, error);
		return (n, cx) -> value.apply(cx.getValue(), cx.getActual())
				.thenApply(v -> cx.mapKey(key, v))
				.thenCompose(cv -> onNext(n, cv))
				.whenCompleteExceptionally(error::onError);
	}

	Promise<?> onNext(ChannelName name, Committed<K, V> committed);
}