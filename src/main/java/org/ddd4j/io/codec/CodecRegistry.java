package org.ddd4j.io.codec;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.ddd4j.Require;
import org.ddd4j.util.Type;

public class CodecRegistry implements Codec.Factory {

	public static CodecRegistry withFallback(Codec.Factory fallback) {
		return new CodecRegistry(fallback, Collections.emptyMap());
	}

	private final Codec.Factory fallback;
	private final Map<Type<?>, Codec<?>> codecs;

	private CodecRegistry(Codec.Factory fallback, Map<Type<?>, Codec<?>> codecs) {
		this.fallback = Require.nonNull(fallback);
		this.codecs = Require.nonNull(codecs);
	}

	@Override
	public <T> Codec<T> nonNull(Type<T> type) {
		@SuppressWarnings("unchecked")
		Codec<T> codec = (Codec<T>) codecs.get(type);
		if (codec == null) {
			fallback.nonNull(type);
		}
		return Require.nonNull(codec);
	}

	public <T> CodecRegistry with(Class<T> type, Codec<T> codec) {
		return with(Type.of(type), codec);
	}

	public <T> CodecRegistry with(Class<T> type, Decoder<? extends T> decoder, Encoder<? super T> encoder) {
		return with(Type.of(type), Codec.of(decoder, encoder));
	}

	public <T> CodecRegistry with(Type<T> type, Codec<T> codec) {
		Map<Type<?>, Codec<?>> copy = new HashMap<>(codecs);
		copy.put(Require.nonNull(type), Require.nonNull(codec));
		return new CodecRegistry(fallback, copy);
	}

	public <T> CodecRegistry with(Type<T> type, Decoder<? extends T> decoder, Encoder<? super T> encoder) {
		return with(type, Codec.of(decoder, encoder));
	}
}