package org.ddd4j.infrastructure.channel.api;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

@FunctionalInterface
public interface CommitListener<K, V> {

	void onNext(ChannelName name, Committed<K, V> committed);

	default <X, Y> CommitListener<X, Y> mapPromised(Function<? super X, K> key, BiFunction<? super Y, Revision, Promise<V>> value,
			ErrorListener listener) {
		Require.nonNullElements(key, value, listener);
		return (n, cx) -> value.apply(cx.getValue(), cx.getActual()).thenApply(v -> cx.mapKey(key, v)).whenComplete(cv -> onNext(n, cv),
				listener::onError);
	}
}