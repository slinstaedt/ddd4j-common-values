package org.ddd4j.spi;

import org.ddd4j.value.Type;
import org.ddd4j.value.collection.Seq;

@FunctionalInterface
public interface ServiceProviderLoader {

	<P extends ServiceProvider<?>> Seq<P> load(Type<P> providerType);
}