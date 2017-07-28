package org.ddd4j.infrastructure.channel.util;

import java.util.function.Function;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.value.versioned.Committed;

public interface SourceListener<K, V> {

	void onNext(ResourceDescriptor resource, Committed<K, V> committed);

	default <X, Y> SourceListener<X, Y> mapPromised(Function<? super X, K> key, Function<? super Y, Promise<V>> value) {
		// TODO handle exception?
		return (r, cx) -> value.apply(cx.getValue()).thenApply(v -> cx.mapKey(key, v)).whenCompleteSuccessfully(cv -> onNext(r, cv));
	}
}