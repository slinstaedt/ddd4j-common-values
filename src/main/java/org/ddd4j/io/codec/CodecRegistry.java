package org.ddd4j.io.codec;

import org.ddd4j.Require;
import org.ddd4j.util.Type;

public class CodecRegistry implements Decoder.Factory, Encoder.Factory {

	public static CodecRegistry withFallback(Decoder.Factory decoderFallback, Encoder.Factory encoderFallback) {
		return new CodecRegistry(Decoder.registry(decoderFallback), Encoder.registry(encoderFallback));
	}

	private final Decoder.Registry decoders;
	private final Encoder.Registry encoders;

	private CodecRegistry(Decoder.Registry decoders, Encoder.Registry encoders) {
		this.decoders = Require.nonNull(decoders);
		this.encoders = Require.nonNull(encoders);
	}

	@Override
	public <T> Decoder<T> decoder(Type<T> readerType) {
		return decoders.decoder(readerType);
	}

	@Override
	public <T> Encoder<T> encoder(Type<T> writerType) {
		return encoders.encoder(writerType);
	}

	public <T> CodecRegistry with(Class<T> type, Decoder<? extends T> decoder, Encoder<? super T> encoder) {
		return with(Type.of(type), decoder, encoder);
	}

	public <T> CodecRegistry with(Type<T> type, Decoder<? extends T> decoder, Encoder<? super T> encoder) {
		return new CodecRegistry(decoders.with(type, decoder), encoders.with(type, encoder));
	}
}