package org.ddd4j.value.config;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.ddd4j.io.Bytes;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.util.value.Value;
import org.ddd4j.value.collection.Seq;

public class Props extends Value.Simple<Props, Map<String, ?>> implements Configuration {

	public static class Entry implements Value<Entry> {

		private final String key;
		private final Bytes bytes;

		public Entry(ReadBuffer buffer) {
			this.key = buffer.getUTF();
			this.bytes = null;
		}

		public Entry(String key, Bytes value) {
			this.key = Require.nonEmpty(key);
			this.bytes = Require.nonNull(value).readOnly();
		}

		public String getKey() {
			return key;
		}

		public ReadBuffer getValue() {
			return bytes.buffered();
		}

		@Override
		public void serialize(WriteBuffer buffer) {
			buffer.putUTF(key).put(getValue());
		}
	}

	public static final Charset CODEC = StandardCharsets.UTF_8;
	public static final Props EMPTY = new Props(Collections.emptyMap());

	public static Props deserialize(ReadBuffer buffer) {
		return new Props(buffer);
	}

	private final Map<String, Entry> entries;

	private Props(Map<String, Entry> values) {
		this.entries = new HashMap<>();
	}

	private Props(Props copy, Consumer<Props> changes) {
		this.entries = new HashMap<>(copy.entries);
		changes.accept(this);
	}

	public Props(ReadBuffer buffer) {
		int count = buffer.getInt();
		this.entries = new HashMap<>(count);
		for (int i = 0; i < count; i++) {
			Entry entry = new Entry(buffer);
			entries.put(entry.key, entry);
		}
	}

	public Optional<Entry> get(String key) {
		return Optional.ofNullable(entries.get(key));
	}

	@Override
	public Optional<String> getString(String key) {
		return get(key).map(e -> new String(e.bytes.toByteArray(), CODEC));
	}

	@Override
	public void serialize(WriteBuffer buffer) {
		buffer.putInt(entries.size());
		entries.values().forEach(e -> e.serialize(buffer));
	}

	public Seq<Entry> toSeq() {
		return entries.values()::stream;
	}

	@Override
	protected Map<String, ?> value() {
		return entries;
	}

	public Props with(Entry entry) {
		return new Props(this, p -> p.entries.put(entry.key, entry));
	}

	public Props with(String key, Bytes value) {
		return with(new Entry(key, value));
	}

	public Props with(String key, String value) {
		return with(key, Bytes.wrap(value.getBytes(CODEC)));
	}

	public Props without(String key) {
		return entries.containsKey(key) ? new Props(this, p -> p.entries.remove(key)) : this;
	}
}
