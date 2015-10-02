package org.ddd4j.value;

public interface Self<S extends Self<S>> {

	S self();
}
