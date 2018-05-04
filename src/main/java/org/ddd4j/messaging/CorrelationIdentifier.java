package org.ddd4j.messaging;

import org.ddd4j.util.value.Value;

public interface CorrelationIdentifier extends Value<CorrelationIdentifier> {

	CorrelationIdentifier UNUSED = new CorrelationIdentifier() {
	};
}
