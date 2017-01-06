package org.ddd4j.infrastructure.messaging;

import org.ddd4j.infrastructure.Result;
import org.ddd4j.value.versioned.Revisions;

public interface EventBus<E> {

	Result<E> events(Revisions revision);
}
