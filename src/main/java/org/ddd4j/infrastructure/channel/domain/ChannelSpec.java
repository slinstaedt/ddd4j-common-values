package org.ddd4j.infrastructure.channel.domain;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.ddd4j.Require;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.value.Type;
import org.ddd4j.value.Type.Variable;
import org.ddd4j.value.Value;

public interface ChannelSpec<K, V> {

	interface Inherited<K extends Value<K>, V> extends ChannelSpec<K, V> {

		@Override
		default K deserializeKey(ReadBuffer buffer) {
			// XXX reflection?
			return getKeyType().constructor(Type.of(ReadBuffer.class)).evaluate(buffer);
		}

		@Override
		default Type<K> getKeyType() {
			Variable<ChannelSpec<K, V>, K> var = Type.variable(ChannelSpec.class, 0, Value.class);
			return Type.ofInstance(this).resolve(var);
		}

		@Override
		default Type<V> getValueType() {
			Variable<ChannelSpec<K, V>, V> var = Type.variable(ChannelSpec.class, 1, Object.class);
			return Type.ofInstance(this).resolve(var);
		}

		@Override
		default void serializeKey(K key, WriteBuffer buffer) {
			key.serialize(buffer);
		}
	}

	static <K extends Value<K>, V> ChannelSpec<K, V> of(ChannelName name, Type<K> keyType, Type<V> valueType,
			Function<ReadBuffer, K> keyDeserializer, BiConsumer<K, WriteBuffer> keySerializer) {
		Require.nonNullElements(name, keyType, valueType, keyDeserializer, keySerializer);
		return new ChannelSpec<K, V>() {

			@Override
			public ChannelName getName() {
				return name;
			}

			@Override
			public K deserializeKey(ReadBuffer buffer) {
				return keyDeserializer.apply(buffer);
			}

			@Override
			public Type<K> getKeyType() {
				return keyType;
			}

			@Override
			public Type<V> getValueType() {
				return valueType;
			}

			@Override
			public void serializeKey(K key, WriteBuffer buffer) {
				keySerializer.accept(key, buffer);
			}
		};
	}

	ChannelName getName();

	K deserializeKey(ReadBuffer buffer);

	Type<K> getKeyType();

	Type<V> getValueType();

	void serializeKey(K key, WriteBuffer buffer);
}