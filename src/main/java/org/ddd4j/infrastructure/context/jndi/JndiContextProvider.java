package org.ddd4j.infrastructure.context.jndi;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.ddd4j.infrastructure.context.Context;
import org.ddd4j.infrastructure.context.ContextProvider;
import org.ddd4j.spi.ServiceLocator;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.collection.Configuration;

public class JndiContextProvider implements ContextProvider {

	@Override
	public Context provideService(Configuration configuration, ServiceLocator locator) {
		try {
			InitialContext context = new InitialContext();
			return new JndiContext(context, configuration.getString("prefix").orElse(null));
		} catch (NamingException e) {
			return Throwing.unchecked(e);
		}
	}
}
