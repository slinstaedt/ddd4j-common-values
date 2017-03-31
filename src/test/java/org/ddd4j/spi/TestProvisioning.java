package org.ddd4j.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.ddd4j.aggregate.AggregateConfigurer;

public class TestProvisioning implements ContextProvisioning {

	private final List<Object> registered;

	public TestProvisioning() {
		this.registered = new ArrayList<>();
	}

	@Override
	public <T> Iterable<T> loadRegistered(Class<T> type, ClassLoader loader) {
		return registered.stream().filter(type::isInstance).map(type::cast).collect(Collectors.toList());
	}

	public void withConfigurer(ServiceConfigurer configurer) {
		registered.add(configurer);
	}

	public void withAggregate(AggregateConfigurer configurer) {
		registered.add(configurer);
	}

	public void with(Object any) {
		registered.add(any);
	}
}