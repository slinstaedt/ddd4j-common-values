package org.ddd4j.schema;

public class SchemaEntry {

	public static SchemaEntry create(SchemaFactory factory, Class<?> type) {
		factory.createSchema(type);
	}

	private Fingerprint fingerprint;
	private String schemaProvider;
	private Schema<?> schema;
}
