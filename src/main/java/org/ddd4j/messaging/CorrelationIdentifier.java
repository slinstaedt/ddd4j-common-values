package org.ddd4j.messaging;

import org.ddd4j.value.Value;

public interface CorrelationIdentifier extends Value<CorrelationIdentifier> {

	CorrelationIdentifier UNUSED = new CorrelationIdentifier() {
	};
}
