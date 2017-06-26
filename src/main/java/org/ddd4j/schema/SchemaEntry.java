package org.ddd4j.schema;

import org.ddd4j.Require;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.value.Value;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revisions;

public class SchemaEntry<T> extends Value.Simple<SchemaEntry<T>, Fingerprint> {

	public static <T> SchemaEntry<T> create(SchemaFactory factory, Class<T> type) {
		Schema<T> schema = factory.createSchema(type);
		return new SchemaEntry<>(factory.name(), schema);
	}

	public static SchemaEntry<?> deserialize(Context context, ReadBuffer buffer) {
		String schemaFactoryName = buffer.getUTF();
		SchemaFactory factory = context.specific(SchemaFactory.KEY).withOrFail(schemaFactoryName);
		Schema<?> schema = factory.readSchema(buffer);
		return new SchemaEntry<>(schemaFactoryName, schema);
	}

	private final String schemaFactoryName;
	private final Schema<T> schema;

	public SchemaEntry(String schemaFactoryName, Schema<T> schema) {
		this.schemaFactoryName = Require.nonEmpty(schemaFactoryName);
		this.schema = Require.nonNull(schema);
	}

	public Fingerprint getFinterprint() {
		return schema.getFingerprint();
	}

	public Schema<T> getSchema() {
		return schema;
	}

	public String getSchemaFactoryName() {
		return schemaFactoryName;
	}

	@Override
	public void serialize(WriteBuffer buffer) {
		buffer.putUTF(schemaFactoryName);
		buffer.accept(schema::serialize);
	}

	public Recorded<ReadBuffer, ReadBuffer> toRecorded(WriteBuffer buffer) {
		return Recorded.uncommitted(value().asBuffer(), buffer.accept(this::serialize).flip(), Revisions.NONE);
	}

	@Override
	protected Fingerprint value() {
		return schema.getFingerprint();
	}
}
