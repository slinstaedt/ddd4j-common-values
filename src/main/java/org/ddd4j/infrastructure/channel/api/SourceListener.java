package org.ddd4j.infrastructure.channel.api;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public interface SourceListener<K, V> {

	default void onError(Throwable throwable) {
		// ignore
	}

	void onNext(ChannelName name, Committed<K, V> committed);

	default <X, Y> SourceListener<X, Y> mapPromised(Function<? super X, K> key, BiFunction<? super Y, Revision, Promise<V>> value) {
		// TODO handle exception?
		return (n, cx) -> value.apply(cx.getValue(), cx.getActual()).thenApply(v -> cx.mapKey(key, v)).whenComplete(cv -> onNext(n, cv),
				this::onError);
	}
}