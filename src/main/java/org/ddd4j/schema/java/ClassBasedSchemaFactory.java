package org.ddd4j.schema.java;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.schema.Fingerprint;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.util.Type;
import org.ddd4j.value.Value;

public class ClassBasedSchemaFactory implements SchemaFactory {

	private class JavaSchema<T> extends Value.Simple<Schema<T>, Class<T>> implements Schema<T> {

		private final Class<T> baseType;

		public JavaSchema(Class<T> baseType) {
			this.baseType = Require.nonNull(baseType);
		}

		@Override
		public boolean compatibleWith(Schema<?> existing) {
			return existing.<JavaSchema>as(JavaSchema.class).map(o -> o.baseType).map(baseType::equals).orElse(Boolean.FALSE);
		}

		@Override
		public <X> Reader<X> createReader(Type<X> readerType) {
			return buf -> Throwing.Producer.of(Throwing.TFunction.of(ObjectInputStream::new).apply(buf.asInputStream())::readObject)
					.map(readerType::cast)
					.get();

		}

		@Override
		public Writer<T> createWriter() {
			return (buf, val) -> Throwing.TFunction.of(ObjectOutputStream::new).apply(buf.asOutputStream()).writeObject(val);
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
		public String getFactoryName() {
			return ClassBasedSchemaFactory.this.getName();
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
	public <T> Schema<T> createSchema(Type<T> type) {
		return new JavaSchema<>(type.getRawType());
	}

	@Override
	public Schema<?> readSchema(ReadBuffer buffer) {
		return new JavaSchema<>(SchemaFactory.classForName(buffer.getUTF(), e -> {
			throw e;
		}));
	}
}
