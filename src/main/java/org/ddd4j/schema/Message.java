package org.ddd4j.schema;

import org.ddd4j.Require;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.value.Type;

public class Message {

	public static Message serialize(SchemaFactory factory, WriteBuffer buffer, Object body) {
		Class<Object> type = Type.ofInstance(body).getRawType();
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

	public <T> T unwrap(Class<T> readerType) {
		body.mark();
		try {
			return writerSchema.createReader(readerType).read(body);
		} finally {
			body.reset();
		}
	}
}
