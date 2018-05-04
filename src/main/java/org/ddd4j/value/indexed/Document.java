package org.ddd4j.value.indexed;

import java.util.Map;

import org.ddd4j.util.Require;

public class Document<T> {

	private final T document;
	private final Map<String, Object> values;

	Document(T document, Map<String, Object> values) {
		this.document = Require.nonNull(document);
		this.values = Require.nonNull(values);
	}
}