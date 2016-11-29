package org.ddd4j.value;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.ddd4j.contract.Require;
import org.ddd4j.io.Output;

public interface Value<V extends Value<V>> extends Self<V> {

	abstract class Simple<V extends Value<V>, T> implements Value<V> {

		private transient IntSupplier hasher;
		private transient Supplier<String> stringer;

		protected Simple() {
			this(v -> v.getClass().getSimpleName() + "(", v -> ")");
		}

		protected Simple(Function<? super V, String> prefix, Function<? super V, String> postfix) {
			hasher = () -> {
				Object value = value();
				int hashCode = value instanceof Object[] ? Arrays.deepHashCode((Object[]) value) : Objects.hashCode(value);
				hasher = () -> hashCode;
				return hashCode;
			};
			stringer = () -> {
				Object value = value();
				String toString = prefix.apply(self()) + deepToString(value) + postfix.apply(self());
				stringer = () -> toString;
				return toString;
			};
		}

		@Override
		public boolean equals(Object obj) {
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
		public int hashCode() {
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

	abstract class StringBased<V extends StringBased<V>> extends Simple<V, String> {

		private final String value;

		public StringBased(String value) {
			super(v -> "", v -> "");
			this.value = Require.nonNull(value);
		}

		@Override
		public void serialize(Output output) throws IOException {
			output.asDataOutput().writeUTF(value);
		}

		@Override
		public String value() {
			return value;
		}
	}

	static String deepToString(Object o) {
		if (o != null && o.getClass().isArray()) {
			Class<?> componentType = o.getClass().getComponentType();
			if (componentType == boolean.class) {
				return Arrays.toString((boolean[]) o);
			} else if (componentType == byte.class) {
				return Arrays.toString((byte[]) o);
			} else if (componentType == char.class) {
				return Arrays.toString((char[]) o);
			} else if (componentType == double.class) {
				return Arrays.toString((double[]) o);
			} else if (componentType == float.class) {
				return Arrays.toString((float[]) o);
			} else if (componentType == int.class) {
				return Arrays.toString((int[]) o);
			} else if (componentType == long.class) {
				return Arrays.toString((long[]) o);
			} else if (componentType == short.class) {
				return Arrays.toString((short[]) o);
			} else {
				return Arrays.toString((Object[]) o);
			}
		} else {
			return String.valueOf(o);
		}
	}

	default <X extends V> Opt<X> as(Class<X> type) {
		return type.isInstance(this) ? Opt.of(type.cast(this)) : Opt.none();
	}

	default void serialize(Output output) throws IOException {
		throw new UnsupportedOperationException();
	}
}
