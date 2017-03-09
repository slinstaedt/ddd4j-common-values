package org.ddd4j.value;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.ddd4j.Throwing;
import org.ddd4j.contract.Require;
import org.ddd4j.value.collection.Tpl;

public interface Either<L, R> {

	class Left<L, R> extends Value.Simple<Left<L, R>, L> implements Either<L, R> {

		private final L value;

		public Left(L value) {
			this.value = value;
		}

		@Override
		public <X> X fold(Function<? super L, ? extends X> left, Function<? super R, ? extends X> right) {
			return left.apply(value);
		}

		@Override
		public String toString() {
			return "Left[" + value + "]";
		}

		@Override
		protected L value() {
			return value;
		}
	}

	@FunctionalInterface
	interface OrBoth<L, R> extends Either<Either<L, R>, Tpl<L, R>> {

		static <L, R> OrBoth<L, R> both(Tpl<L, R> tpl) {
			return Either.<Either<L, R>, Tpl<L, R>> right(Require.nonNull(tpl))::fold;
		}

		static <L, R> OrBoth<L, R> left(L value) {
			return Either.<Either<L, R>, Tpl<L, R>> left(Either.left(value))::fold;
		}

		static <L, R> OrBoth<L, R> right(R value) {
			return Either.<Either<L, R>, Tpl<L, R>> left(Either.right(value))::fold;
		}

		default Tpl<L, R> getBoth() {
			return getRight();
		}

		default Either<L, R> getEither() {
			return getLeft();
		}
	}

	class Right<L, R> extends Value.Simple<Right<L, R>, R> implements Either<L, R> {

		private final R value;

		public Right(R value) {
			this.value = value;
		}

		@Override
		public <X> X fold(Function<? super L, ? extends X> left, Function<? super R, ? extends X> right) {
			return right.apply(value);
		}

		@Override
		public String toString() {
			return "Right[" + value + "]";
		}

		@Override
		protected R value() {
			return value;
		}
	}

	static <L, R> Either<L, R> left(L value) {
		return new Either<L, R>() {

			@Override
			public <X> X fold(Function<? super L, ? extends X> left, Function<? super R, ? extends X> right) {
				return left.apply(value);
			}
		};
	}

	static <L, R> Either<L, R> right(R value) {
		return new Either<L, R>() {

			@Override
			public <X> X fold(Function<? super L, ? extends X> left, Function<? super R, ? extends X> right) {
				return right.apply(value);
			}
		};
	}

	static <L, R> Either<L, R> when(boolean condition, Supplier<? extends L> left, Supplier<? extends R> right) {
		return condition ? left(left.get()) : right(right.get());
	}

	default void consume(Consumer<? super L> left, Consumer<? super R> right) {
		fold(l -> {
			left.accept(l);
			return null;
		}, (r) -> {
			right.accept(r);
			return null;
		});
	}

	default <X, Y> Either<? extends X, ? extends Y> flatMap(Function<? super L, Either<? extends X, ? extends Y>> left,
			Function<? super R, Either<? extends X, ? extends Y>> right) {
		return fold(left, right);
	}

	default <X> Either<? extends X, ? extends R> flatMapLeft(Function<? super L, Either<? extends X, ? extends R>> left) {
		return fold(left, Function.<R> identity().andThen(Either::<X, R> right));
	}

	default <Y> Either<? extends L, ? extends Y> flatMapRight(Function<? super R, Either<? extends L, ? extends Y>> right) {
		return fold(Function.<L> identity().andThen(Either::<L, Y> left), right);
	}

	<X> X fold(Function<? super L, ? extends X> left, Function<? super R, ? extends X> right);

	default R foldLeft(Function<? super L, ? extends R> left) {
		return fold(left, Function.identity());
	}

	default L foldRight(Function<? super R, ? extends L> right) {
		return fold(Function.identity(), right);
	}

	default L getLeft() {
		return foldRight(Throwing.of(IllegalStateException::new).asFunction());
	}

	default R getRight() {
		return foldLeft(Throwing.of(IllegalStateException::new).asFunction());
	}

	default boolean isLeft() {
		return fold(l -> Boolean.TRUE, r -> Boolean.FALSE);
	}

	default boolean isRight() {
		return fold(l -> Boolean.FALSE, r -> Boolean.TRUE);
	}

	default <X, Y> Either<X, Y> map(Function<? super L, ? extends X> left, Function<? super R, ? extends Y> right) {
		return fold(left.andThen(Either::<X, Y> left), right.andThen(Either::<X, Y> right));
	}

	default <X> Either<X, R> mapLeft(Function<? super L, ? extends X> left) {
		return fold(left.andThen(Either::<X, R> left), Function.<R> identity().andThen(Either::<X, R> right));
	}

	default <X> Optional<? extends X> mapOptional(Function<? super L, ? extends X> left, Function<? super R, ? extends X> right) {
		return fold(left.andThen(Optional::ofNullable), right.andThen(Optional::ofNullable));
	}

	default <Y> Either<L, Y> mapRight(Function<? super R, ? extends Y> right) {
		return fold(Function.<L> identity().andThen(Either::<L, Y> left), right.andThen(Either::<L, Y> right));
	}

	default Either<R, L> swap() {
		return fold(Either::<R, L> right, Either::<R, L> left);
	}

	default boolean test(Predicate<? super L> left, Predicate<? super R> right) {
		return fold(left::test, right::test);
	}
}
