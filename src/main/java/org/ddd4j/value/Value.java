package org.ddd4j.value;

import org.ddd4j.io.ByteDataOutput;

public interface Value<V extends Value<V>> extends Self<V> {

	class Wrapper<V extends Value<V>> {

		private final V value;

		public Wrapper(V value) {
			this.value = value;
		}

		public V value() {
			return value;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((value == null) ? 0 : value.hash());
			return result;
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
			@SuppressWarnings("unchecked")
			Wrapper<V> other = (Wrapper<V>) obj;
			if (value == null) {
				if (other.value != null) {
					return false;
				}
			} else if (!value.equal(other.value)) {
				return false;
			}
			return true;
		}

		@Override
		public String toString() {
			return String.valueOf(value);
		}
	}

	default Wrapper<V> wrapped() {
		return new Wrapper<>(self());
	}

	default <X extends V> Opt<X> as(Class<X> type) {
		return type.isInstance(this) ? Opt.of(type.cast(this)) : Opt.none();
	}

	default void serialize(ByteDataOutput output) {
		throw new UnsupportedOperationException();
	}

	int hash();

	boolean equal(V other);
}
