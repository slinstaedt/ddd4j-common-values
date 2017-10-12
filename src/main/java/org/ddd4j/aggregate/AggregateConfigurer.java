package org.ddd4j.aggregate;

import java.util.function.Consumer;

import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.ServiceConfigurer;
import org.ddd4j.value.Named;

public interface AggregateConfigurer extends Named {

	class AggregateServiceConfigurer implements ServiceConfigurer.Registered<AggregateConfigurer> {

		@Override
		public Consumer<AggregateConfigurer> configurer(Context context) {
			return c -> c.configure(context.get(Scheduler.KEY));
		}
	}

	void configure(Scheduler scheduler);
}
