package org.ddd4j.aggregate;

import java.util.function.Consumer;

import org.ddd4j.log.Log;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.ContextProvisioning;
import org.ddd4j.spi.ServiceConfigurer;
import org.ddd4j.value.Named;
import org.ddd4j.value.collection.Props;

public interface AggregateConfigurer extends Named {

	class AggregateServiceConfigurer implements ServiceConfigurer.Registered<AggregateConfigurer> {

		@Override
		public Consumer<AggregateConfigurer> configurer(Context context) {
			return c -> c.configure(context.get(Log.KEY));
		}
	}

	void configure(Log log);

	public static void main(String[] args) {
		Context context = ContextProvisioning.byJavaServiceLoader().createContext(Props.EMTPY);
	}
}
