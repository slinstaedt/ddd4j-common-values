package org.ddd4j.aggregate;

import java.util.function.Consumer;

import org.ddd4j.log.Log;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.ServiceProvider;
import org.ddd4j.value.Named;
import org.ddd4j.value.collection.Configuration;

public interface AggregateConfigurer extends Named {

	class AggregateServiceProvider implements ServiceProvider.Registered<AggregateConfigurer> {

		@Override
		public Consumer<AggregateConfigurer> configurer(Context context, Configuration configuration) {
			return c -> c.configure(context.get(Log.KEY));
		}
	}

	void configure(Log log);
}
