package org.ddd4j.value.owner;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;
import java.util.function.Supplier;

import org.ddd4j.value.Either;
import org.ddd4j.value.owner.XXX.Owned;
import org.ddd4j.value.owner.XXX.Unclaimed;

@FunctionalInterface
public interface XXX<T> extends Either<Owned<T>, Unclaimed<T>> {

	interface Owned<T> {

		default Contract<T, Void> donate(Object newOwner) {
			registry();
			return null;
		}

		<T> Contract<T, T> exchangeWith(Owned<T> other);

		OwnerRegistry registry();
	}

	interface Unclaimed<T> extends Supplier<T> {
	}

	static <T> XXX<T> owned(Owned<T> item) {
		requireNonNull(item);
		return new XXX<T>() {

			@Override
			public <X> X fold(Function<? super XXX.Owned<T>, ? extends X> left,
					Function<? super XXX.Unclaimed<T>, ? extends X> right) {
				return left.apply(item);
			}
		};
	}

	static <T> XXX<T> shared(Unclaimed<T> item) {
		requireNonNull(item);
		return new XXX<T>() {

			@Override
			public <X> X fold(Function<? super XXX.Owned<T>, ? extends X> left,
					Function<? super XXX.Unclaimed<T>, ? extends X> right) {
				return right.apply(item);
			}
		};
	}
}
