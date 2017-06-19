package org.ddd4j.repository;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.repository.api.Reader;
import org.ddd4j.repository.api.Writer;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.SchemaFactory;

public enum SchemaCodingStrategy {

	PER_MESSAGE {

		@Override
		public <T> Schema.Reader<T> reader(Reader.Factory repo, SchemaFactory factory, Class<T> type) {
			return buf -> factory.readSchema(buf).createReader(type).read(buf);
		}

		@Override
		public <T> Schema.Writer<T> writer(Writer.Factory repo, SchemaFactory factory, Class<T> type) {
			Schema<T> schema = factory.createSchema(type);
			Schema.Writer<T> writer = schema.createWriter();
			return (buf, val) -> buf.put(encode()).accept(schema::serialize).accept(b -> writer.write(b, val));
		}
	},
	IN_BAND {

		@Override
		public <T> Schema.Reader<T> reader(Reader.Factory repo, SchemaFactory factory, Class<T> readerType) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public <T> Schema.Writer<T> writer(Writer.Factory repo, SchemaFactory factory, Class<T> writerType) {
			// TODO Auto-generated method stub
			return null;
		}
	},
	OUT_OF_BAND {

		@Override
		public <T> Schema.Reader<T> reader(Reader.Factory repo, SchemaFactory factory, Class<T> type) {
			Reader<ReadBuffer, ReadBuffer> repository = repo.create(SCHEMATA);
			return buf -> repository.get(factory.readFingerprint(buf).asBuffer())
					.thenApply(c -> factory.readSchema(c.getValue()).createReader(type).read(buf))
					.join();
		}

		@Override
		public <T> Schema.Writer<T> writer(Writer.Factory repo, SchemaFactory factory, Class<T> type) {
			// TODO Auto-generated method stub
			Writer<ReadBuffer, ReadBuffer> repository = repo.create(SCHEMATA);
			Schema<T> schema = factory.createSchema(type);
			Schema.Writer<T> writer = schema.createWriter();
			return (buf, val) -> writer.write(buf.put(encode()), val);
		}
	};

	public static final ResourceDescriptor SCHEMATA = ResourceDescriptor.of("schemata");

	public static SchemaCodingStrategy decode(byte b) {
		return SchemaCodingStrategy.values()[b];
	}

	byte encode() {
		return (byte) ordinal();
	}

	public abstract <T> Schema.Reader<T> reader(Reader.Factory repository, SchemaFactory factory, Class<T> readerType);

	public abstract <T> Schema.Writer<T> writer(Writer.Factory repository, SchemaFactory factory, Class<T> writerType);
}