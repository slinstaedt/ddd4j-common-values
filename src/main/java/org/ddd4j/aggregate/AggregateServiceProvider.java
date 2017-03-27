package org.ddd4j.aggregate;

import org.ddd4j.spi.Context;
import org.ddd4j.spi.ContextProvisioning;
import org.ddd4j.spi.ServiceProvider;
import org.ddd4j.value.collection.Configuration;

public class AggregateServiceProvider implements ServiceProvider.Eager {

	@Override
	public void initialize(Context context, Configuration configuration) throws Exception {
		// TODO Auto-generated method stub
		Iterable<? extends Eager> registered = context.get(ContextProvisioning.KEY).loadRegistered(getClass());
	}
}
