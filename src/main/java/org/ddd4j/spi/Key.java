package org.ddd4j.spi;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
import org.ddd4j.Throwing.TConsumer;
import org.ddd4j.value.Named;
import org.ddd4j.value.Value;

//TODO rename to ServiceKey?
public class Key<T> implements ServiceFactory<T>, Named {

	private static class Child<T> extends Key<T> implements Value<Child<T>> {

		private final Key<T> parent;

		Child(Key<T> parent, String childName) {
			super(parent.name + "." + Require.nonEmpty(childName), parent.creator, parent.destructor);
			this.parent = Require.nonNull(parent);
		}

		@Override
		public int hashCode() {
			return name().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			Child<?> other = (Child<?>) obj;
			return this.parent.equals(other.parent) && this.name().equals(other.name());
		}
	}

	public static <T> Key<T> of(Class<? extends T> serviceType) {
		return of(Named.decapitalize(serviceType));
	}

	public static <T> Key<T> of(Class<? extends T> serviceType, ServiceFactory<? extends T> creator) {
		return of(Named.decapitalize(serviceType), creator);
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

	Key(String name, ServiceFactory<? extends T> creator, TConsumer<? super T> destructor) {
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

	public Key<T> child(String childName) {
		return new Child<>(this, childName);
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
