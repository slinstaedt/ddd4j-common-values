package org.ddd4j.schema.java;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import org.ddd4j.contract.Require;
import org.ddd4j.io.Input;
import org.ddd4j.io.Output;
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
		public void serialize(Output output) throws IOException {
			output.asDataOutput().writeUTF(baseType.getName());
		}

		@Override
		public int hashCode(Object object) {
			return Objects.hashCode(object);
		}

		@Override
		public Reader<T> createReader(Input input) {
			ObjectInput in = Throwing.ofApplied(Input::asObjectInput).apply(input);
			return Throwing.ofSupplied(in::readObject).map(baseType::cast)::get;
		}

		@Override
		public Writer<T> createWriter(Output output) {
			ObjectOutput out = Throwing.ofApplied(Output::asObjectOutput).apply(output, true);
			return (datum, flush) -> {
				if (flush) {
					out.flush();
				} else {
					out.writeObject(datum);
				}
			};
		}

		@Override
		public boolean compatibleWith(Schema<?> existing) {
			return existing.<JavaSchema>as(JavaSchema.class).mapNonNull(o -> o.baseType).checkEqual(baseType);
		}

		@Override
		protected Object value() {
			return baseType;
		}

		@Override
		public boolean equal(Object o1, Object o2) {
			return Objects.deepEquals(o1, o2);
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
		public void serialize(Output output) throws IOException {
			output.asDataOutput().writeUTF(baseType.getName());
		}
	}

	@Override
	public <T> Schema<T> createSchema(Class<T> type) {
		return new JavaSchema<>(type);
	}

	@Override
	public Fingerprint readFingerprint(Input input) throws IOException {
		return new JavaFingerprint(SchemaFactory.classForName(input.asDataInput().readUTF(), Throwing.rethrow()));
	}

	@Override
	public Schema<?> readSchema(Input input) throws IOException {
		return new JavaSchema<>(SchemaFactory.classForName(input.asDataInput().readUTF(), Throwing.rethrow()));
	}
}
