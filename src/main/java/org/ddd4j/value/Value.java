package org.ddd4j.value;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.ddd4j.io.Output;

public interface Value<V extends Value<V>> extends Self<V> {

	abstract class Simple<V extends Value<V>> implements Value<V> {

		private IntSupplier hasher = () -> {
			Object value = value();
			int hashCode = value instanceof Object[] ? Arrays.deepHashCode((Object[]) value) : Objects.hashCode(value);
			hasher = () -> hashCode;
			return hashCode;
		};

		private Supplier<String> stringer = () -> {
			Object value = value();
			String toString = value instanceof Object[] ? Arrays.asList((Object[]) value).toString() : String.valueOf(value);
			stringer = () -> toString;
			return toString;
		};

		protected abstract Object value();

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			} else if (obj == null) {
				return false;
			} else if (getClass() != obj.getClass()) {
				return false;
			} else {
				return Objects.deepEquals(value(), ((Simple<?>) obj).value());
			}
		}

		@Override
		public int hashCode() {
			return hasher.getAsInt();
		}

		@Override
		public String toString() {
			return stringer.get();
		}
	}

	default <X extends V> Opt<X> as(Class<X> type) {
		return type.isInstance(this) ? Opt.of(type.cast(this)) : Opt.none();
	}

	default void serialize(Output output) throws IOException {
		throw new UnsupportedOperationException();
	}
}
