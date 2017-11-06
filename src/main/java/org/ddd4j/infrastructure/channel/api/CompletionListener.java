package org.ddd4j.infrastructure.channel.api;

import org.ddd4j.infrastructure.Promise;

@FunctionalInterface
public interface CompletionListener {

	CompletionListener VOID = Promise::completed;

	Promise<?> onComplete();
}
