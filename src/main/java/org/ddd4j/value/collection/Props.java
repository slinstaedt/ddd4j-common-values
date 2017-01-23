package org.ddd4j.value.collection;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.value.Throwing;
import org.ddd4j.value.collection.Seq.Value;

public class Props extends Value.Simple<Configuration, Properties> implements Configuration {

	public static final Props EMTPY = new Props(Collections.EMPTY_MAP);

	private final Properties values;

	public Props(ReadBuffer buffer) {
		this.values = new Properties();
		try {
			values.load(buffer.asInputStream());
		} catch (IOException e) {
			Throwing.unchecked(e);
		}
	}

	public Props(Map<?, ?> copy) {
		this.values = new Properties();
		this.values.putAll(copy);
	}

	private Props(Properties defaults, String key, String value) {
		this.values = new Properties(defaults);
		this.values.put(key, value);
	}

	@Override
	public Optional<String> getString(String key) {
		return Optional.ofNullable(values.getProperty(key));
	}

	@Override
	public Stream<Tpl<String, String>> stream() {
		return values.entrySet().stream().map(e -> Tpl.of(String.valueOf(e.getKey()), String.valueOf(e.getValue())));
	}

	@Override
	public <V> Props with(Key<V> key, V value) {
		return key.toConfig(this::with, value);
	}

	@Override
	public Props with(String key, String value) {
		return new Props(values, key, value);
	}

	@Override
	protected Properties value() {
		return values;
	}

	@Override
	public void serialize(WriteBuffer buffer) {
		try {
			values.store(buffer.asOutputStream(), null);
		} catch (IOException e) {
			Throwing.unchecked(e);
		}
	}
}
