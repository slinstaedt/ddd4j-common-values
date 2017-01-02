package org.ddd4j.infrastructure.context;

import org.ddd4j.spi.Service;

public interface Context extends Service<Context, ContextProvider> {

	<T> T lookup(ContextualIdentifier<T> identifier);
}
