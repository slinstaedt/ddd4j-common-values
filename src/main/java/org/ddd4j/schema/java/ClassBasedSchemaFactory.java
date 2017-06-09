package org.ddd4j.schema.java;

import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Objects;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.schema.Fingerprint;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.value.Value;

public class ClassBasedSchemaFactory implements SchemaFactory {

	static class JavaSchema<T> extends Value.Simple<Schema<T>, Class<T>> implements Schema<T> {

		private final Class<T> baseType;

		public JavaSchema(Class<T> baseType) {
			this.baseType = Require.nonNull(baseType);
		}

		@Override
		public boolean compatibleWith(Schema<?> existing) {
			return existing.<JavaSchema> as(JavaSchema.class).mapNonNull(o -> o.baseType).checkEqual(baseType);
		}

		@Override
		public <X> Reader<X> createReader(ReadBuffer buffer, Class<X> readerType) {
			ObjectInput in = Throwing.applied(ObjectInputStream::new).apply(buffer.asInputStream());
			return Throwing.task(in::readObject).map(readerType::cast)::get;
		}

		@Override
		public Writer<T> createWriter(WriteBuffer buffer) {
			ObjectOutput out = Throwing.applied(ObjectOutputStream::new).apply(buffer.asOutputStream());
			return out::writeObject;
		}

		@Override
		public boolean equal(Object o1, Object o2) {
			return Objects.deepEquals(o1, o2);
		}

		@Override
		public Fingerprint getFingerprint() {
			return new Fingerprint(baseType.getName().getBytes());
		}

		@Override
		public String getName() {
			return baseType.getName();
		}

		@Override
		public int hashCode(Object object) {
			return Objects.hashCode(object);
		}

		@Override
		public void serialize(WriteBuffer buffer) {
			buffer.putUTF(baseType.getName());
		}

		@Override
		protected Class<T> value() {
			return baseType;
		}
	}

	@Override
	public <T> Schema<T> createSchema(Class<T> type) {
		return new JavaSchema<>(type);
	}

	@Override
	public Schema<?> readSchema(ReadBuffer buffer) {
		return new JavaSchema<>(SchemaFactory.classForName(buffer.getUTF(), Throwing.rethrow()));
	}
}
