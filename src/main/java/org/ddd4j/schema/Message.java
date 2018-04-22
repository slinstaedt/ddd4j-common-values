package org.ddd4j.schema;

import org.ddd4j.Require;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Type;

// TODO delete?
public class Message {

	public static Message serialize(SchemaFactory factory, WriteBuffer buffer, Object body) {
		Type<Object> type = Type.ofInstance(body);
		Schema<Object> writerSchema = factory.createSchema(type);
		writerSchema.createWriter().write(buffer, body);
		return new Message(writerSchema, buffer.flip());
	}

	private final Schema<?> writerSchema;
	private final ReadBuffer body;

	public Message(Schema<?> writerSchema, ReadBuffer body) {
		this.writerSchema = Require.nonNull(writerSchema);
		this.body = Require.nonNull(body);
	}

	public <T> T unwrap(Type<T> readerType) {
		body.mark();
		try {
			return writerSchema.createReader(readerType).read(body);
		} finally {
			body.reset();
		}
	}
}
