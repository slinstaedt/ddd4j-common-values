package org.ddd4j.spi;

import org.ddd4j.value.Self;
import org.ddd4j.value.Type;

public interface Service<S extends Service<S, P>, P extends ServiceProvider<S>> extends Self<S> {

	Type.Variable<Service<?, ?>, ServiceProvider<?>> P = Type.variable(Service.class, 1, ServiceProvider.class);

	default Configuration getConfiguration() {
		return Configuration.NONE;
	}

	default String getServiceName() {
		return getClass().getName();
	}
}
