package org.ddd4j.value.versioned;

@FunctionalInterface
public interface EventProcessor<E, T> {

	T applyEvent(Committed<E> event);
}