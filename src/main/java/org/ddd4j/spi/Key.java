package org.ddd4j.spi;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
import org.ddd4j.Throwing.TConsumer;
import org.ddd4j.value.Named;

//TODO rename to ServiceKey?
public final class Key<T> implements ServiceFactory<T>, Named {

	public static <T> Key<T> of(Class<? extends T> serviceType) {
		return of(Named.decapitalize(serviceType.getSimpleName()));
	}

	public static <T> Key<T> of(Class<? extends T> serviceType, ServiceFactory<? extends T> creator) {
		return of(Named.decapitalize(serviceType.getSimpleName()), creator);
	}

	public static <T> Key<T> of(String name) {
		return of(name, ctx -> Throwing.of(AssertionError::new).throwChecked("No factory is bound for key: " + name));
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
	public T create(Context context) throws Exception {
		return creator.create(context);
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
