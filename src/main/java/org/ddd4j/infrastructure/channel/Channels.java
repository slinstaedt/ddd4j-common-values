package org.ddd4j.infrastructure.channel;

import java.util.function.Consumer;

import org.ddd4j.infrastructure.Pool;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.spi.Committer;
import org.ddd4j.infrastructure.channel.spi.Writer;
import org.ddd4j.infrastructure.codec.CodecFactory;
import org.ddd4j.infrastructure.codec.Decoder;
import org.ddd4j.infrastructure.codec.Encoder;
import org.ddd4j.infrastructure.domain.value.ChannelSpec;
import org.ddd4j.infrastructure.publisher.Publisher;
import org.ddd4j.infrastructure.publisher.RevisionCallback;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Ref;
import org.ddd4j.util.Require;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revision;

public class Channels {

	public interface DecodingFactory<K, V> {

		CommitListener<ReadBuffer, ReadBuffer> create(CommitListener<K, V> commit, ErrorListener error);
	}

	public static final Ref<Channels> REF = Ref.of(Channels.class, Channels::new);

	private static <R extends Recorded<ReadBuffer, ReadBuffer>> R withBuffers(R recorded, Consumer<ReadBuffer> fn) {
		fn.accept(recorded.getKey());
		fn.accept(recorded.getValue());
		return recorded;
	}

	private final Context context;

	public Channels(Context context) {
		this.context = Require.nonNull(context);
	}

	private WriteBuffer buf() {
		return context.get(Pool.BUFFERS).get();
	}

	private CodecFactory codecFactory() {
		return context.get(CodecFactory.REF);
	}

	public <K, V> Committer<K, V> createCommitter(ChannelSpec<K, V> spec) {
		return encodingCommitter(spec, context.get(Committer.FACTORY).createCommitterClosingBuffers(spec.getName()));
	}

	public <K, V> Committer<K, V> createSubmitter(ChannelSpec<K, V> spec) {
		Committer<ReadBuffer, ReadBuffer> committer = context.get(Committer.FACTORY).createCommitter(spec.getName());
		Writer<ReadBuffer, ReadBuffer> writer = context.get(Writer.FACTORY).createWriter(spec.getName());
		Committer<ReadBuffer, ReadBuffer> submitter = attempt -> committer.commit(withBuffers(attempt, ReadBuffer::mark))
				.thenRun(() -> withBuffers(attempt, ReadBuffer::reset))
				// compiler's type inference failed
				.thenCompose(r -> r.<Promise<? extends CommitResult<ReadBuffer, ReadBuffer>>>onCommitted(writer::put, Promise.completed(r)))
				.thenRun(() -> withBuffers(attempt, ReadBuffer::reset));
		return encodingCommitter(spec, submitter);
	}

	public <K, V> Writer<K, V> createWriter(ChannelSpec<K, V> spec) {
		return encodingWriter(spec, context.get(Writer.FACTORY).createWriterClosingBuffers(spec.getName()));
	}

	public <K, V> DecodingFactory<K, V> decodingFactory(ChannelSpec<K, V> spec) {
		Decoder<K> key = spec.getKeyType().decoder(codecFactory());
		Decoder<V> value = spec.getValueType().decoder(codecFactory());
		return (c, e) -> c.mapPromised(key::decode, value::decode, e);
	}

	public <K, V> Committer<K, V> encodingCommitter(ChannelSpec<K, V> spec, Committer<ReadBuffer, ReadBuffer> committer) {
		Encoder<K> key = spec.getKeyType().encoder(codecFactory());
		Encoder<V> value = spec.getValueType().encoder(codecFactory());
		Promise.Deferred<Revision> revision = context.get(Scheduler.REF).createDeferredPromise();
		return committer.mapPromised(revision, //
				k -> key.encode(buf(), revision, k).thenApply(WriteBuffer::flip),
				v -> value.encode(buf(), revision, v).thenApply(WriteBuffer::flip));
	}

	public <K, V> Writer<K, V> encodingWriter(ChannelSpec<K, V> spec, Writer<ReadBuffer, ReadBuffer> writer) {
		Encoder<K> key = spec.getKeyType().encoder(codecFactory());
		Encoder<V> value = spec.getValueType().encoder(codecFactory());
		Promise.Deferred<Revision> revision = context.get(Scheduler.REF).createDeferredPromise();
		return writer.mapPromised(revision, //
				k -> key.encode(buf(), revision, k).thenApply(WriteBuffer::flip),
				v -> value.encode(buf(), revision, v).thenApply(WriteBuffer::flip));
	}

	public <K, V> Publisher<K, V, RevisionCallback> publisher(ChannelSpec<K, V> spec) {
		return context.get(RevisionCallback.PUBLISHER).publisher(spec.getName(), decodingFactory(spec));
	}

	public <K, V> void subscribe(ChannelSpec<K, V> spec, CommitListener<K, V> commit, ErrorListener error, RevisionCallback callback) {
		CommitListener<ReadBuffer, ReadBuffer> decoded = decodingFactory(spec).create(commit, error);
		context.get(RevisionCallback.PUBLISHER).subscribe(spec.getName(), commit, decoded, error, callback);
	}
}
