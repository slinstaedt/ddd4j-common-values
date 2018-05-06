package org.ddd4j.infrastructure.domain.value;

import org.ddd4j.infrastructure.domain.codec.CodecType;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.util.Require;
import org.ddd4j.util.Type;
import org.ddd4j.util.value.Value;

public class ChannelSpec<K, V> {

	public static <K extends Value<K>, V> ChannelSpec<K, V> of(ChannelName name, Type<K> keyType, Type<V> valueType) {
		return new ChannelSpec<>(name, CodecType.manual(buf -> keyType.constructor(Type.of(ReadBuffer.class)).evaluate(buf), K::serialize),
				CodecType.simple(name, valueType));
	}

	private final ChannelName name;
	private final CodecType<K> keyType;
	private final CodecType<V> valueType;

	public ChannelSpec(ChannelName name, CodecType<K> keyType, CodecType<V> valueType) {
		this.name = Require.nonNull(name);
		this.keyType = Require.nonNull(keyType);
		this.valueType = Require.nonNull(valueType);
	}

	public CodecType<K> getKeyType() {
		return keyType;
	}

	public ChannelName getName() {
		return name;
	}

	public CodecType<V> getValueType() {
		return valueType;
	}
}