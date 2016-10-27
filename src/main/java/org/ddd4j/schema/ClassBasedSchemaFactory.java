package org.ddd4j.schema;

import java.util.Optional;

import org.ddd4j.contract.Require;
import org.ddd4j.schema.Schema.Fingerprint;
import org.ddd4j.schema.Schema.Type;
import org.ddd4j.value.collection.Seq;

public class ClassBasedSchemaFactory implements Schema.Factory {

	static class JavaSchema implements Schema, Fingerprint {

		private Package javaPackage;

		public Package javaPackage() {
			return javaPackage;
		}

		@Override
		public String getName() {
			return javaPackage.getName();
		}

		@Override
		public JavaSchema getFingerprint() {
			return this;
		}

		@Override
		public boolean contains(Type<?> type) {
			if (type instanceof JavaType) {
				return ((JavaType<?>) type).javaType.getPackage().equals(javaPackage);
			} else {
				return false;
			}
		}

		@Override
		public int hash() {
			return javaPackage.hashCode();
		}

		@Override
		public boolean equal(Fingerprint other) {
			return other.as(JavaSchema.class).map(s -> s.javaPackage.equals(javaPackage)).orElse(false);
		}
	}

	static class JavaType<T> implements Type<T> {

		private final Class<T> javaType;

		public JavaType(Class<T> javaType) {
			this.javaType = Require.nonNull(javaType);
		}

		@Override
		public Optional<T> cast(Object object) {
			return javaType.isInstance(object) ? Optional.of(javaType.cast(object)) : Optional.empty();
		}
	}

	private final Seq<JavaSchema> schemata;

	public ClassBasedSchemaFactory() {
		schemata = Seq.empty();
	}

	@Override
	public Optional<? extends Schema> lookup(Fingerprint fingerprint) {
		return fingerprint.as(JavaSchema.class)
				.map(JavaSchema::javaPackage)
				.map(p -> schemata.map().mapped(JavaSchema::javaPackage).reversed().get(p))
				.flatMap(Seq::head);
	}

	@Override
	public Schema schema(Type<?> type) {
		return null;
	}
}
