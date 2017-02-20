package org.ddd4j.value.versioned;

//TODO needed?
@FunctionalInterface
public interface EventProcessor<K, V, T> {

	T applyEvent(Committed<K, V> event);
}