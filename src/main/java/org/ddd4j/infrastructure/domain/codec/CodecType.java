package org.ddd4j.infrastructure.domain.codec;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.ddd4j.infrastructure.codec.CodecFactory;
import org.ddd4j.infrastructure.codec.Decoder;
import org.ddd4j.infrastructure.codec.Encoder;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.io.codec.Decoding;
import org.ddd4j.io.codec.Encoding;
import org.ddd4j.util.Require;
import org.ddd4j.util.Type;

//TODO remove dependency  from CodecFactory
public interface CodecType<T> {

	static <T, S> CodecType<T> codec(ChannelName name, Type<S> type, Encoding<S, T> encoding, Decoding<Supplier<S>, T> decoding) {
		return new Coded<>(name, type, encoding, decoding);
	}

	static <T> CodecType<T> manual(Function<ReadBuffer, ? extends T> deserializer, BiConsumer<? super T, WriteBuffer> serializer) {
		return new Manual<>(deserializer, serializer);
	}

	static <T> CodecType<T> simple(ChannelName name, Type<T> type) {
		return new Simple<>(name, type);
	}

	Decoder<T> decoder(CodecFactory factory);

	Encoder<T> encoder(CodecFactory factory);
}

class Coded<T, S> implements CodecType<T> {

	private final ChannelName name;
	private final Type<S> type;
	private final Encoding<S, T> encoding;
	private final Decoding<Supplier<S>, T> decoding;

	Coded(ChannelName name, Type<S> type, Encoding<S, T> encoding, Decoding<Supplier<S>, T> decoding) {
		this.name = Require.nonNull(name);
		this.type = Require.nonNull(type);
		this.encoding = Require.nonNull(encoding);
		this.decoding = Require.nonNull(decoding);
	}

	@Override
	public Decoder<T> decoder(CodecFactory factory) {
		return factory.decoder(name, type, decoding);
	}

	@Override
	public Encoder<T> encoder(CodecFactory factory) {
		return factory.encoder(name, type, encoding);
	}
}

class Manual<T> implements CodecType<T> {

	private final Function<ReadBuffer, ? extends T> deserializer;
	private final BiConsumer<? super T, WriteBuffer> serializer;

	Manual(Function<ReadBuffer, ? extends T> deserializer, BiConsumer<? super T, WriteBuffer> serializer) {
		this.deserializer = Require.nonNull(deserializer);
		this.serializer = Require.nonNull(serializer);
	}

	@Override
	public Decoder<T> decoder(CodecFactory factory) {
		return Decoder.from(deserializer);
	}

	@Override
	public Encoder<T> encoder(CodecFactory factory) {
		return Encoder.from(serializer);
	}
}

class Simple<T> implements CodecType<T> {

	private final ChannelName name;
	private final Type<T> type;

	Simple(ChannelName name, Type<T> type) {
		this.name = Require.nonNull(name);
		this.type = Require.nonNull(type);
	}

	@Override
	public Decoder<T> decoder(CodecFactory factory) {
		return factory.decoder(name, type);
	}

	@Override
	public Encoder<T> encoder(CodecFactory factory) {
		return factory.encoder(name, type);
	}
}