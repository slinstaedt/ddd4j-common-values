package org.ddd4j.infrastructure.channel.api;

@FunctionalInterface
public interface ErrorListener {

	void onError(Throwable throwable);
}
