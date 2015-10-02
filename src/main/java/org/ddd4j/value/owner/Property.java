package org.ddd4j.value.owner;

import static java.util.Objects.requireNonNull;

import java.util.function.BiPredicate;
import java.util.function.Function;

import org.ddd4j.value.Either;
import org.ddd4j.value.owner.Property.Private;
import org.ddd4j.value.owner.Property.Public;

@FunctionalInterface
public interface Property<T> extends Either<Public<T>, Private<T>> {

	@FunctionalInterface
	interface Private<T> {

		<R> R apply(Function<? super T, R> action);
	}

	@FunctionalInterface
	interface Public<T> {

		T get();
	}

	static <T> Property<T> checked(T item, BiPredicate<? super T, Function<? super T, ?>> predicate) {
		requireNonNull(item);
		requireNonNull(predicate);
		Private<T> privateProperty = new Private<T>() {

			@Override
			public <R> R apply(Function<? super T, R> action) {
				if (predicate.test(item, action)) {
					return action.apply(item);
				} else {
					throw new IllegalArgumentException("Is not allowed to access: " + action);
				}
			}
		};
		return new Property<T>() {

			@Override
			public <X> X fold(Function<? super Property.Public<T>, ? extends X> left,
					Function<? super Property.Private<T>, ? extends X> right) {
				return right.apply(privateProperty);
			}
		};
	}

	static <T> Property<T> shared(T item) {
		requireNonNull(item);
		Public<T> publicProperty = () -> item;
		return new Property<T>() {

			@Override
			public <X> X fold(Function<? super Property.Public<T>, ? extends X> left,
					Function<? super Property.Private<T>, ? extends X> right) {
				return left.apply(publicProperty);
			}
		};
	}
}
