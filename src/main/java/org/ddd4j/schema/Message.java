package org.ddd4j.schema;

import org.ddd4j.Require;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.value.Type;
import org.ddd4j.value.Value;

public class Message implements Value<Message> {

	public static Message serialize(SchemaFactory factory, WriteBuffer buffer, Object body) {
		Class<Object> type = Type.ofInstance(body).getRawType();
		Schema<Object> writerSchema = factory.createSchema(type);
		writerSchema.createWriter(buffer).write(body);
		return new Message(writerSchema, buffer.flip());
	}

	private final Schema<?> writerSchema;
	private final ReadBuffer body;

	public Message(Schema<?> writerSchema, ReadBuffer body) {
		this.writerSchema = Require.nonNull(writerSchema);
		this.body = Require.nonNull(body);
	}

	public <T> T unwrap(Class<T> targetType) {
		body.mark();
		try {
			return writerSchema.createReader(body, targetType).read();
		} finally {
			body.reset();
		}
	}

	@Override
	public void serialize(WriteBuffer buffer) {
		writerSchema.serialize(buffer);
		// TODO buffer.put
	}
}
