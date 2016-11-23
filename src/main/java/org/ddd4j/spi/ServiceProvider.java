package org.ddd4j.spi;

@FunctionalInterface
public interface ServiceProvider<S extends Service> {

	S provideService(Configuration configuration);
}
