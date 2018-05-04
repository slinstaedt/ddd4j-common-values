package org.ddd4j.value.indexed;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.ddd4j.util.Require;
import org.ddd4j.value.collection.Seq;

public class Indexer<D> {

	public enum Capability {
		STORE_SOURCE_DOCUMENT, PROVIDE_GLOBAL_SEARCH;
	}

	private final Set<Capability> capabilities;
	private final Map<String, Property<D, ?>> properties;

	Indexer(Capability... capabilities) {
		this.capabilities = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(capabilities)));
		this.properties = Collections.emptyMap();
	}

	private Indexer(Indexer<D> copy, Property<D, ?> property) {
		Require.nonNull(property);
		Require.that(!copy.properties.containsKey(property.getName()));
		this.capabilities = copy.capabilities;
		this.properties = new HashMap<>(copy.properties);
		this.properties.put(property.getName(), property);
	}

	public Set<Capability> getCapabilities() {
		return capabilities;
	}

	public Seq<Property<D, ?>> getProperties() {
		return Seq.ofCopied(properties.values());
	}

	public <X extends D> Document<X> index(X document) {
		Map<String, Object> values = new HashMap<>();
		properties.entrySet().forEach(e -> values.put(e.getKey(), e.getValue().readValue(document)));
		return new Document<>(document, values);
	}

	public Indexer<D> with(Property<D, ?> property) {
		return new Indexer<>(this, property);
	}
}