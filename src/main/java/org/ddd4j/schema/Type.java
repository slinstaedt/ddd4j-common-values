package org.ddd4j.schema;

import java.util.Optional;

import org.ddd4j.contract.Require;

@FunctionalInterface
public interface Type<T> {

	class JavaType<T> implements Type<T> {

		private final Class<T> javaType;

		public JavaType(Class<T> javaType) {
			this.javaType = Require.nonNull(javaType);
		}

		@Override
		public Optional<T> cast(Object object) {
			return javaType.isInstance(object) ? Optional.of(javaType.cast(object)) : Optional.empty();
		}
	}

	Optional<T> cast(Object object);

	default boolean partOf(Schema schema) {
		return schema.contains(this);
	}
}