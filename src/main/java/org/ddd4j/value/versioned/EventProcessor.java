package org.ddd4j.value.versioned;

@FunctionalInterface
public interface EventProcessor<K, V, T> {

	T applyEvent(Committed<K, V> event);
}