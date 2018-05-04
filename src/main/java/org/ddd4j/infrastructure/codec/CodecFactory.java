package org.ddd4j.infrastructure.codec;

import java.util.function.Supplier;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.codec.compressed.CompressedSchemaCodec;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.codec.Decoding;
import org.ddd4j.io.codec.Encoding;
import org.ddd4j.schema.Schema;
import org.ddd4j.schema.Schema.Writer;
import org.ddd4j.schema.SchemaFactory;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Ref;
import org.ddd4j.util.Lazy;
import org.ddd4j.util.Require;
import org.ddd4j.util.Type;
import org.ddd4j.util.value.Monad;
import org.ddd4j.value.config.ConfKey;

public class CodecFactory {

	public static final Ref<CodecFactory> REF = Ref.of(CodecFactory.class, CodecFactory::new);
	private static final ConfKey<String> SCHEMA_CODEC = ConfKey.ofString("schemaCodec", CompressedSchemaCodec.NAME);

	private final Context context;

	CodecFactory(Context context) {
		this.context = Require.nonNull(context);
	}

	public <T> Decoder<T> decoder(ChannelName name, Type<T> readerType) {
		return decoder(name, readerType, Decoding.readValue());
	}

	public <T, R> Decoder<R> decoder(ChannelName name, Type<T> readerType, Decoding<Supplier<T>, R> decoding) {
		Require.nonNulls(name, readerType, decoding);
		return (buf, rev) -> {
			Lazy<Monad<Supplier<T>>> schemaReader = Lazy.of(() -> context.specific(SchemaCodec.REF) //
					.withOrFail(buf.getUTF()) // TODO use smaller storage footprint than String
					.decode(buf, rev, name)
					.thenApply(s -> s.createReader(readerType).asSupplier(buf)));
			return decoding.decode(buf, Promise::completed, schemaReader).casted();
		};
	}

	public <T> Encoder<T> encoder(ChannelName name, Type<T> writerType) {
		return encoder(name, writerType, Encoding.writeValue());
	}

	public <T, R> Encoder<R> encoder(ChannelName name, Type<T> writerType, Encoding<T, R> encoding) {
		Require.nonNulls(name, writerType, encoding);
		SchemaCodec codec = context.specific(SchemaCodec.REF, SCHEMA_CODEC);
		Schema<T> schema = context.get(SchemaFactory.REF).createSchema(writerType);
		Writer<T> writer = schema.createWriter();
		return (buf, rev, val) -> {
			Lazy<Promise<?>> schemaWriter = Lazy.of(() -> codec.encode(buf.putUTF(codec.name()), rev, name, schema));
			encoding.encode(val, buf, schemaWriter.<T>asConsumer().andThen(writer.asConsumer(buf)));
			return schemaWriter.ifPresent(p -> p.thenReturnValue(buf), () -> Promise.completed(buf));
		};
	}
}
