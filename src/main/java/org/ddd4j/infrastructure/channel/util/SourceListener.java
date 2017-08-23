package org.ddd4j.infrastructure.channel.util;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public interface SourceListener<K, V> {

	void onNext(ChannelName resource, Committed<K, V> committed);

	default <X, Y> SourceListener<X, Y> mapPromised(Function<? super X, K> key, BiFunction<? super Y, Revision, Promise<V>> value) {
		// TODO handle exception?
		return (r, cx) -> value.apply(cx.getValue(), cx.getActual())
				.thenApply(v -> cx.mapKey(key, v))
				.whenCompleteSuccessfully(cv -> onNext(r, cv));
	}
}