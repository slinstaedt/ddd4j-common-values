package org.ddd4j.repository;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.repository.api.Reader;
import org.ddd4j.repository.api.Writer;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.SchemaEntry;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.spi.Context;
import org.ddd4j.value.versioned.Recorded;

public enum SchemaCodingStrategy {

	PER_MESSAGE {

		@Override
		public <T> Decoder<T> decoder(Context context, Class<T> type) {
			SchemaFactory factory = context.get(SchemaFactory.KEY);
			return buf -> Promise.completed(factory.readSchema(buf).createReader(type).read(buf));
		}

		@Override
		public <T> Encoder<T> encoder(Context context, Class<T> type) {
			Schema<T> schema = context.get(SchemaFactory.KEY).createSchema(type);
			Schema.Writer<T> writer = schema.createWriter();
			return (buf, val) -> Promise.completed(buf.put(encode()).accept(schema::serialize).accept(b -> writer.write(b, val)));
		}
	},
	IN_BAND {

		@Override
		public <T> Decoder<T> decoder(Context context, Class<T> type) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public <T> Encoder<T> encoder(Context context, Class<T> type) {
			// TODO Auto-generated method stub
			return null;
		}
	},
	OUT_OF_BAND {

		@Override
		public <T> Decoder<T> decoder(Context context, Class<T> type) {
			SchemaFactory factory = context.get(SchemaFactory.KEY);
			Reader<ReadBuffer, ReadBuffer> repository = repo.create(SCHEMATA);
			return buf -> repository.get(factory.readFingerprint(buf).asBuffer()).thenApply(c -> {
				SchemaEntry.deserialize(context, buffer);
				return context.readSchema(c.getValue()).createReader(type).read(buf);
			});
		}

		@Override
		public <T> Encoder<T> encoder(Context context, Class<T> type) {
			SchemaFactory factory = context.get(SchemaFactory.KEY);
			SchemaEntry<T> entry = SchemaEntry.create(factory, type);
			context.get(Writer.Factory.KEY).create(SCHEMATA).put(entry.asRecorded());
			Schema.Writer<T> writer = entry.getSchema().createWriter();
			return (buf, val) -> {
				Recorded.uncommitted(buffer, expected);
				writer.write(buf.put(encode()), val);
				return null;
			};
		}
	};

	interface Decoder<T> {

		Promise<T> decode(ReadBuffer buffer);
	}

	interface Encoder<T> {

		Promise<WriteBuffer> encode(WriteBuffer buffer, T value);
	}

	public static final ResourceDescriptor SCHEMATA = ResourceDescriptor.of("schemata");

	public static SchemaCodingStrategy decode(byte b) {
		return SchemaCodingStrategy.values()[b];
	}

	byte encode() {
		return (byte) ordinal();
	}

	public abstract <T> Decoder<T> decoder(Context context, Class<T> readerType);

	public abstract <T> Encoder<T> encoder(Context context, Class<T> writerType);
}