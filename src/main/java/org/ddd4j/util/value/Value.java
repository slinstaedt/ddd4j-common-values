package org.ddd4j.util.value;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.util.Self;

public interface Value<V extends Value<V>> extends Self<V> {

	abstract class Complex<V extends Complex<V>> extends Simple<V, Value<?>[]> {

		@Override
		public void serialize(WriteBuffer buffer) {
			for (Value<?> value : value()) {
				value.serialize(buffer);
			}
		}
	}

	abstract class Simple<V extends Value<V>, T> implements Value<V> {

		private transient IntSupplier hasher;
		private transient Supplier<String> stringer;

		protected Simple() {
			this(v -> v.getClass().getSimpleName() + "(", v -> ")");
		}

		protected Simple(Function<? super V, String> prefix, Function<? super V, String> postfix) {
			hasher = () -> {
				T value = value();
				int hashCode = calculateHash(value);
				hasher = () -> hashCode;
				return hashCode;
			};
			stringer = () -> {
				T value = value();
				String toString = prefix.apply(self()) + deepToString(value) + postfix.apply(self());
				stringer = () -> toString;
				return toString;
			};
		}

		protected int calculateHash(T value) {
			return deepHashCode(value);
		}

		@Override
		public final boolean equals(Object obj) {
			if (this == obj) {
				return true;
			} else if (obj == null) {
				return false;
			} else if (getClass() != obj.getClass()) {
				return false;
			} else {
				@SuppressWarnings("unchecked")
				Simple<?, T> other = (Simple<?, T>) obj;
				return testEquality(this.value(), other.value());
			}
		}

		@Override
		public final int hashCode() {
			return hasher.getAsInt();
		}

		protected boolean testEquality(T o1, T o2) {
			return Objects.deepEquals(o1, o2);
		}

		@Override
		public String toString() {
			return stringer.get();
		}

		protected abstract T value();
	}

	abstract class StringBased<V extends Value<V>> extends Simple<V, String> {

		private final String value;

		public StringBased(String value) {
			super(v -> "", v -> "");
			this.value = Require.nonNull(value);
		}

		@Override
		public void serialize(WriteBuffer buffer) {
			buffer.putUTF(value);
		}

		@Override
		public String value() {
			return value;
		}
	}

	static int deepHashCode(Object o) {
		if (o instanceof boolean[]) {
			return Arrays.hashCode((boolean[]) o);
		} else if (o instanceof byte[]) {
			return Arrays.hashCode((byte[]) o);
		} else if (o instanceof char[]) {
			return Arrays.hashCode((char[]) o);
		} else if (o instanceof double[]) {
			return Arrays.hashCode((double[]) o);
		} else if (o instanceof float[]) {
			return Arrays.hashCode((float[]) o);
		} else if (o instanceof int[]) {
			return Arrays.hashCode((int[]) o);
		} else if (o instanceof long[]) {
			return Arrays.hashCode((long[]) o);
		} else if (o instanceof short[]) {
			return Arrays.hashCode((short[]) o);
		} else if (o instanceof Object[]) {
			return Arrays.deepHashCode((Object[]) o);
		} else {
			return Objects.hashCode(o);
		}
	}

	static String deepToString(Object o) {
		if (o instanceof boolean[]) {
			return Arrays.toString((boolean[]) o);
		} else if (o instanceof byte[]) {
			return Arrays.toString((byte[]) o);
		} else if (o instanceof char[]) {
			return Arrays.toString((char[]) o);
		} else if (o instanceof double[]) {
			return Arrays.toString((double[]) o);
		} else if (o instanceof float[]) {
			return Arrays.toString((float[]) o);
		} else if (o instanceof int[]) {
			return Arrays.toString((int[]) o);
		} else if (o instanceof long[]) {
			return Arrays.toString((long[]) o);
		} else if (o instanceof short[]) {
			return Arrays.toString((short[]) o);
		} else if (o instanceof Object[]) {
			return Arrays.toString((Object[]) o);
		} else {
			return String.valueOf(o);
		}
	}

	default <X> Optional<X> as(Class<X> type) {
		return type.isInstance(this) ? Optional.of(type.cast(this)) : Optional.empty();
	}

	default void serialize(WriteBuffer buffer) {
		throw new UnsupportedOperationException();
	}
}
