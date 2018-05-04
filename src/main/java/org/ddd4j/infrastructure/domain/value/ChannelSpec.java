package org.ddd4j.infrastructure.domain.value;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.util.Type;
import org.ddd4j.util.value.Value;

public class ChannelSpec<K, V> {

	public static <K extends Value<K>, V> ChannelSpec<K, V> of(ChannelName name, Type<K> keyType, Type<V> valueType) {
		return new ChannelSpec<>(name, keyType, valueType, buf -> keyType.constructor(Type.of(ReadBuffer.class)).evaluate(buf),
				K::serialize);
	}

	public static <K, V> ChannelSpec<K, V> of(ChannelName name, Type<K> keyType, Type<V> valueType, Function<ReadBuffer, K> keyDeserializer,
			BiConsumer<K, WriteBuffer> keySerializer) {
		return new ChannelSpec<>(name, keyType, valueType, keyDeserializer, keySerializer);
	}

	public static <K extends Value<K>, V> ChannelSpec<K, V> of(String name, Class<K> keyType, Class<V> valueType) {
		return of(ChannelName.of(name), Type.of(keyType), Type.of(valueType));
	}

	private final ChannelName name;
	private final Type<K> keyType;
	private final Type<V> valueType;
	private final Function<ReadBuffer, K> keyDeserializer;
	private final BiConsumer<K, WriteBuffer> keySerializer;

	public ChannelSpec(ChannelName name, Type<K> keyType, Type<V> valueType, Function<ReadBuffer, K> keyDeserializer,
			BiConsumer<K, WriteBuffer> keySerializer) {
		this.name = Require.nonNull(name);
		this.keyType = Require.nonNull(keyType);
		this.valueType = Require.nonNull(valueType);
		this.keyDeserializer = Require.nonNull(keyDeserializer);
		this.keySerializer = Require.nonNull(keySerializer);
	}

	public K deserializeKey(ReadBuffer buffer) {
		return keyDeserializer.apply(buffer);
	}

	public Type<K> getKeyType() {
		return keyType;
	}

	public ChannelName getName() {
		return name;
	}

	public Type<V> getValueType() {
		return valueType;
	}

	public void serializeKey(K key, WriteBuffer buffer) {
		keySerializer.accept(key, buffer);
	}
}