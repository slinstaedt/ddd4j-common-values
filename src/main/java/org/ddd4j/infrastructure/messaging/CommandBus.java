package org.ddd4j.infrastructure.messaging;

import org.ddd4j.infrastructure.Outcome;
import org.ddd4j.infrastructure.Result;
import org.ddd4j.value.behavior.Reaction;
import org.ddd4j.value.function.Curry.Command;
import org.ddd4j.value.function.Curry.Query;

public interface CommandBus<E> {

	Outcome<Reaction<?>> dispatch(Command command);

	<T> Result<T> dispatch(Query<T> query);
}
