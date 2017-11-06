package org.ddd4j.infrastructure.channel.api;

import org.ddd4j.infrastructure.Promise;

@FunctionalInterface
public interface ErrorListener {

	ErrorListener FAIL = Promise::failed;
	ErrorListener IGNORE = Promise::completed;

	Promise<?> onError(Throwable throwable);
}
