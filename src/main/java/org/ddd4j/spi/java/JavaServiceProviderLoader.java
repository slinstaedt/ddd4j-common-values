package org.ddd4j.spi.java;

import java.util.ServiceLoader;

import org.ddd4j.spi.ServiceProvider;
import org.ddd4j.spi.ServiceProviderLoader;
import org.ddd4j.value.Type;
import org.ddd4j.value.collection.Seq;

public class JavaServiceProviderLoader implements ServiceProviderLoader {

	@Override
	public <P extends ServiceProvider<?>> Seq<P> load(Type<P> providerType) {
		Class<P> type = providerType.getRawType();
		ServiceLoader<P> loader = ServiceLoader.load(type, type.getClassLoader());
		return Seq.of(loader);
	}
}
