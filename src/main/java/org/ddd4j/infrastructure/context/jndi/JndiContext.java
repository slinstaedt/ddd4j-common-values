package org.ddd4j.infrastructure.context.jndi;

import javax.naming.NamingException;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.context.Context;
import org.ddd4j.infrastructure.context.ContextualIdentifier;
import org.ddd4j.value.Throwing;

public class JndiContext implements Context {

	private final javax.naming.Context delegate;
	private final String prefix;

	public JndiContext(javax.naming.Context delegate, String prefix) {
		this.delegate = Require.nonNull(delegate);
		this.prefix = prefix != null ? prefix : "";
	}

	@Override
	public <T> T lookup(ContextualIdentifier<T> identifier) {
		try {
			Object lookup = delegate.lookup(prefix + identifier.getName());
			return identifier.getType().cast(lookup);
		} catch (NamingException e) {
			return Throwing.unchecked(e);
		}
	}
}
