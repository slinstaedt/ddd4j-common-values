package org.ddd4j.infrastructure.channel.api;

@FunctionalInterface
public interface CompletionListener {

	void onComplete();
}
