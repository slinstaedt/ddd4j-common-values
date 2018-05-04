package org.ddd4j.infrastructure.domain.header;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Require;

public class Headers {

	public static final Headers EMPTY = new Headers(Collections.emptyMap());

	public static Headers deserialize(ReadBuffer buffer) {
		int size = buffer.getInt();
		Map<String, ReadBuffer> values = new HashMap<>(size);
		for (int i = 0; i < size; i++) {
			values.put(buffer.getUTF(), buffer.duplicate().limitToRemaining(buffer.getInt()));
		}
		return new Headers(values);
	}

	private final Map<String, ReadBuffer> values;

	private Headers(Headers copy, Consumer<Map<String, ReadBuffer>> change) {
		this.values = new HashMap<>(copy.values);
		change.accept(this.values);
	}

	public Headers(Map<String, ReadBuffer> values) {
		this.values = new HashMap<>(values);
	}

	public boolean contains(HeaderKey<?> key) {
		return contains(key.getName());
	}

	public boolean contains(String key) {
		return values.containsKey(key);
	}

	public void forEach(BiConsumer<? super String, ? super ReadBuffer> action) {
		values.forEach((k, v) -> action.accept(k, v.duplicate()));
	}

	public <X> Optional<X> get(HeaderKey<X> key) {
		return get(key.getName()).map(key::deserialize);
	}

	public Optional<ReadBuffer> get(String key) {
		return Optional.ofNullable(values.get(key)).map(ReadBuffer::duplicate);
	}

	public WriteBuffer serialize(WriteBuffer buffer) {
		buffer.putInt(values.size());
		forEach((k, v) -> buffer.putUTF(k).putInt(v.remaining()).put(v));
		return buffer;
	}

	public <X> Headers with(HeaderKey<X> key, X value, WriteBuffer buffer) {
		return with(key.getName(), key.serialize(value, buffer).flip());
	}

	public Headers with(String key, ReadBuffer value) {
		Require.nonNulls(key, value);
		return new Headers(this, m -> m.put(key, value));
	}

	public Headers without(HeaderKey<?> key) {
		return without(key.getName());
	}

	public Headers without(String key) {
		if (contains(key)) {
			return new Headers(this, m -> m.remove(key));
		} else {
			return this;
		}
	}
}
