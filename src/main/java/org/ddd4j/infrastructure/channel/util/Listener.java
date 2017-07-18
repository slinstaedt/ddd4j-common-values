package org.ddd4j.infrastructure.channel.util;

import java.util.function.Function;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.value.versioned.Committed;

public interface Listener<K, V> {

	void onNext(ResourceDescriptor resource, Committed<K, V> committed);

	default <X, Y> Listener<X, Y> map(Function<? super X, ? extends K> key, Function<? super Y, ? extends V> value) {
		return (r, c) -> onNext(r, c.map(key, value));
	}
}