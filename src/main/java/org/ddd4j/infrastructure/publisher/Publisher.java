package org.ddd4j.infrastructure.publisher;

import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.function.Function;

import org.ddd4j.util.Require;
import org.ddd4j.value.versioned.Committed;

public interface Publisher<K, V, C> {

	default Flow.Publisher<Committed<K, V>> forCallback(C callback) {
		Require.nonNull(callback);
		return s -> subscribe(s, callback);
	}

	default Flow.Publisher<Committed<K, V>> forCallback(Function<? super Subscriber<? super Committed<K, V>>, ? extends C> callback) {
		Require.nonNull(callback);
		return s -> subscribe(s, callback.apply(s));
	}

	void subscribe(Subscriber<? super Committed<K, V>> subscriber, C callback);
}