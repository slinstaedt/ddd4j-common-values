package org.ddd4j.spi;

import org.ddd4j.Throwing;
import org.ddd4j.Throwing.TConsumer;
import org.ddd4j.contract.Require;
import org.ddd4j.value.Named;
import org.ddd4j.value.collection.Configuration;

//TODO rename to ServiceKey?
public final class Key<T> implements ServiceFactory<T>, Named {

	public static <T> Key<T> of(Class<T> serviceType) {
		return of(Named.decapitalize(serviceType.getSimpleName()));
	}

	public static <T> Key<T> of(Class<T> serviceType, ServiceFactory<? extends T> creator) {
		return of(Named.decapitalize(serviceType.getSimpleName()), creator);
	}

	public static <T> Key<T> of(String name) {
		return of(name, (ctx, conf) -> Throwing.<T> unchecked(new AssertionError("No factory is bound for: " + name)));
	}

	public static <T> Key<T> of(String name, ServiceFactory<? extends T> creator) {
		return new Key<>(name, creator, Object::getClass);
	}

	private final String name;
	private final ServiceFactory<? extends T> creator;
	private final TConsumer<? super T> destructor;

	public Key(String name, ServiceFactory<? extends T> creator, TConsumer<? super T> destructor) {
		this.name = Require.nonEmpty(name);
		this.creator = Require.nonNull(creator);
		this.destructor = Require.nonNull(destructor);
	}

	@Override
	public T create(Context context, Configuration configuration) throws Exception {
		return creator.create(context, configuration);
	}

	@Override
	public void destroy(T service) throws Exception {
		destructor.acceptChecked(service);
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public Key<T> withDestructor(TConsumer<? super T> destructor) {
		return new Key<>(name, creator, destructor);
	}
}
