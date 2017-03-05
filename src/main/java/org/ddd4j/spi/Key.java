package org.ddd4j.spi;

import java.util.function.Consumer;

import org.ddd4j.contract.Require;
import org.ddd4j.value.collection.Configuration;

public class Key<T> implements ServiceFactory<T>, Named {

	public static <T> Key<T> of(Class<T> serviceType) {
		return of(serviceType.getSimpleName());
	}

	public static <T> Key<T> of(Class<T> serviceType, ServiceFactory<? extends T> creator) {
		return of(serviceType.getSimpleName(), creator);
	}

	public static <T> Key<T> of(String name) {
		return of(name, (ctx, conf) -> null);
	}

	public static <T> Key<T> of(String name, ServiceFactory<? extends T> creator) {
		return new Key<>(name, creator, t -> {
		});
	}

	private final String name;
	private final ServiceFactory<? extends T> creator;
	private final Consumer<? super T> destructor;

	public Key(String name, ServiceFactory<? extends T> creator, Consumer<? super T> destructor) {
		this.name = Require.nonEmpty(name);
		this.creator = Require.nonNull(creator);
		this.destructor = Require.nonNull(destructor);
	}

	@Override
	public T create(Context context, Configuration configuration) {
		return creator.create(context, configuration);
	}

	public void destroy(T service) {
		destructor.accept(service);
	}

	@Override
	public String name() {
		return name;
	}

	public Key<T> withCreator(ServiceFactory<? extends T> creator) {
		return new Key<>(name, creator, destructor);
	}

	public Key<T> withDestructor(Consumer<? super T> destructor) {
		return new Key<>(name, creator, destructor);
	}
}
