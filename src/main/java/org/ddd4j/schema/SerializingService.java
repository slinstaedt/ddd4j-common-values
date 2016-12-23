package org.ddd4j.schema;

import org.ddd4j.value.Self;

public interface SerializingService<S extends SerializingService<S, T>, T> extends Self<S> {

	<X> SerializingService<S, X> withSchema(Schema<X> schema);
}
