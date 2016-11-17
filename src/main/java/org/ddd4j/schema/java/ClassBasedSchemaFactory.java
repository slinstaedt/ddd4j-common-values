package org.ddd4j.schema.java;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import org.ddd4j.contract.Require;
import org.ddd4j.io.ByteDataInput;
import org.ddd4j.io.ByteDataOutput;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.Schema.Fingerprint;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.Value;

public class ClassBasedSchemaFactory implements SchemaFactory {

	static class JavaSchema<T> extends Value.Simple<Schema<T>> implements Schema<T> {

		private final Class<T> baseType;

		public JavaSchema(Class<T> baseType) {
			this.baseType = Require.nonNull(baseType);
		}

		@Override
		public String getName() {
			return baseType.getName();
		}

		@Override
		public Fingerprint getFingerprint() {
			return new JavaFingerprint(baseType);
		}

		@Override
		public void serialize(ByteDataOutput output) {
			output.writeUTF(baseType.getName());
		}

		@Override
		public int hashCode(Object object) {
			return Objects.hashCode(object);
		}

		@Override
		public Reader<T> createReader(ByteDataInput input) {
			ObjectInput in = Throwing.ofSupplied(input::asObject).get();
			return Throwing.ofSupplied(in::readObject).map(baseType::cast)::get;
		}

		@Override
		public Writer<T> createWriter(ByteDataOutput output) {
			ObjectOutput out = Throwing.ofSupplied(() -> output.asObject(true)).get();
			return Throwing.ofConsumed(out::writeObject)::accept;
		}

		@Override
		public boolean compatibleWith(Schema<?> existing) {
			return existing.<JavaSchema>as(JavaSchema.class).mapNonNull(o -> o.baseType).checkEqual(baseType);
		}

		@Override
		protected Object value() {
			return baseType;
		}
	}

	static class JavaFingerprint extends Value.Simple<Fingerprint> implements Fingerprint {

		private final Class<?> baseType;

		public JavaFingerprint(Class<?> baseType) {
			this.baseType = Require.nonNull(baseType);
		}

		@Override
		protected Object value() {
			return baseType;
		}

		@Override
		public void serialize(ByteDataOutput output) {
			output.writeUTF(baseType.getName());
		}
	}

	@Override
	public <T> Schema<T> createSchema(Class<T> type) {
		return new JavaSchema<>(type);
	}

	@Override
	public Fingerprint readFingerprint(ByteDataInput input) {
		return new JavaFingerprint(SchemaFactory.classForName(input.readUTF()));
	}

	@Override
	public Schema<?> readSchema(ByteDataInput input) {
		return new JavaSchema<>(SchemaFactory.classForName(input.readUTF()));
	}
}
