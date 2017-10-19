package org.ddd4j.infrastructure.channel.api;

@FunctionalInterface
public interface ErrorListener {

	ErrorListener VOID = Throwable::getClass;

	void onError(Throwable throwable);
}
