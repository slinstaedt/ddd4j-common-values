package org.ddd4j.spi;

import org.ddd4j.contract.Require;
import org.ddd4j.value.Type;
import org.ddd4j.value.collection.Seq;

public class ServiceLocator {

	private final ServiceProvider.Loader providerLoader;
	private final Configuration.Loader configLoader;

	public ServiceLocator(ServiceProvider.Loader providerLoader, Configuration.Loader configLoader) {
		this.providerLoader = Require.nonNull(providerLoader);
		this.configLoader = Require.nonNull(configLoader);
	}

	public <S extends Service<S, P>, P extends ServiceProvider<S>> S locate(Class<S> serviceType) {
		return locate(Type.of(serviceType));
	}

	public <S extends Service<S, P>, P extends ServiceProvider<S>> S locate(Type<S> serviceType) {
		Type<? extends ServiceProvider<?>> providerType = serviceType.resolve(Service.P);
		Seq<? extends ServiceProvider<?>> providers = providerLoader.load(providerType);
		// TODO how to handle multiple?
		ServiceProvider<?> provider = providers.head().getNonNull();
		Configuration configuration = configLoader.loadFor(provider);
		Service<?, ?> service = provider.provideService(configuration, this);
		return serviceType.cast(service);
	}
}
