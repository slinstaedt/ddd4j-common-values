package org.ddd4j.spi;

import java.util.ServiceLoader;

import org.ddd4j.contract.Require;
import org.ddd4j.infrastructure.queue.Queue;
import org.ddd4j.infrastructure.queue.QueueFactory;
import org.ddd4j.spi.ServiceProvider.Loader;
import org.ddd4j.value.Type;
import org.ddd4j.value.collection.Seq;

public class ServiceLocator {

	public static ServiceLocator create() {
		return new ServiceLocator(Seq.of(ServiceLoader.load(Loader.class)));
	}

	public static void main(String[] args) {
		Queue<String> queue = create().locate(QueueFactory.class).create();
		System.out.println(queue);
	}

	private final Seq<Loader> loaders;

	public ServiceLocator(Seq<Loader> loaders) {
		this.loaders = Require.nonNull(loaders);
	}

	public <S extends Service<S, P>, P extends ServiceProvider<S>> S locate(Class<S> serviceType) {
		return locate(Type.of(serviceType));
	}

	public <S extends Service<S, P>, P extends ServiceProvider<S>> S locate(Type<S> serviceType) {
		Type<? extends ServiceProvider<?>> providerType = serviceType.resolve(Service.P);
		Seq<? extends ServiceProvider<?>> providers = loaders.map().flat(l -> l.load(providerType)).target();
		Service<?, ?> service = providers.head().getNonNull().provideService(Configuration.NONE, this);
		return serviceType.cast(service).get();
	}
}
