package org.ddd4j.spi;

import java.util.function.Predicate;

import org.ddd4j.util.Require;
import org.ddd4j.util.Throwing;
import org.ddd4j.util.Throwing.TConsumer;
import org.ddd4j.util.value.Named;
import org.ddd4j.util.value.Value;

public class Ref<T> implements ServiceFactory<T>, Named {

	// TODO needed?
	private static class Child<T> extends Ref<T> implements Value<Child<T>> {

		private final Ref<T> parent;

		Child(Ref<T> parent, String childName) {
			super(parent.name + "." + Require.nonEmpty(childName), parent.creator, parent.destructor, Context.class::isInstance);
			this.parent = Require.nonNull(parent);
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

		@Override
		public int hashCode() {
			return parent.hashCode() ^ name().hashCode();
		}
	}

	public static <T> Ref<T> of(Class<? extends T> serviceType) {
		return of(Named.decapitalize(serviceType));
	}

	public static <T> Ref<T> of(Class<? extends T> serviceType, ServiceFactory<? extends T> creator) {
		return of(Named.decapitalize(serviceType), creator);
	}

	public static <T> Ref<T> of(String name) {
		return of(name, ctx -> Throwing.of(AssertionError::new).throwChecked("No factory is bound for ref: " + name));
	}

	public static <T> Ref<T> of(String name, ServiceFactory<? extends T> creator) {
		return new Ref<>(name, creator, Object::getClass, Object.class::isInstance);
	}

	public static <T> Ref<T> reflective(Class<T> serviceType) {
		return of(serviceType, ctx -> new ReflectiveServiceFactory<>(serviceType, ctx.configuration()).create());
	}

	private final String name;
	private final ServiceFactory<? extends T> creator;
	private final TConsumer<? super T> destructor;
	private final Predicate<Context> precondition;

	private Ref(String name, ServiceFactory<? extends T> creator, TConsumer<? super T> destructor, Predicate<Context> precondition) {
		this.name = Require.nonEmpty(name);
		this.creator = Require.nonNull(creator);
		this.destructor = Require.nonNull(destructor);
		this.precondition = Require.nonNull(precondition);
	}

	public Ref<T> child(String childName) {
		return new Child<>(this, childName);
	}

	@Override
	public T create(Context context) throws Exception {
		Require.that(precondition.test(context));
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

	public Ref<T> withChecked(Predicate<Context> check) {
		return new Ref<>(name, creator, destructor, precondition.and(check));
	}

	@Override
	public Ref<T> withDestructor(TConsumer<? super T> destructor) {
		return new Ref<>(name, creator, destructor, precondition);
	}
}
