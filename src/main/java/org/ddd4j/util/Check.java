package org.ddd4j.util;

import java.util.function.Consumer;
import java.util.function.Function;

@FunctionalInterface
public interface Check<V, T extends V> {

	interface Result<T> {

		Result<?> NEGATIVE = new Result<Object>() {

			@Override
			public Object getValue() {
				throw new IllegalStateException("Result is negative");
			}

			@Override
			public boolean isPositive() {
				return false;
			}
		};

		@SuppressWarnings("unchecked")
		static <T> Result<T> negative() {
			return (Result<T>) NEGATIVE;
		}

		static <T> Result<T> positive(T value) {
			return new Result<T>() {

				@Override
				public T getValue() {
					return value;
				}

				@Override
				public boolean isPositive() {
					return true;
				}
			};
		}

		T getValue();

		default boolean ifPositive(Consumer<? super T> consumer) {
			return peek(consumer).isPositive();
		}

		boolean isPositive();

		default <R> Result<R> map(Function<? super T, ? extends R> mapper) {
			return isPositive() ? positive(mapper.apply(getValue())) : negative();
		}

		default Result<T> peek(Consumer<? super T> consumer) {
			if (isPositive()) {
				consumer.accept(getValue());
			}
			return this;
		}
	}

	static <V, T extends V> Check<V, T> is(Class<T> type) {
		Require.nonNull(type);
		return v -> type.isInstance(v) ? Result.positive(type.cast(v)) : Result.negative();
	}

	static <V, T extends V> Check<V, T> isNull() {
		return v -> v == null ? Result.positive(null) : Result.negative();
	}

	Result<T> value(V value);
}
