package org.ddd4j.infrastructure.channel;

import java.util.function.Consumer;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
import org.ddd4j.Throwing.TFunction;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.SchemaCodec.DecodingFactory;
import org.ddd4j.infrastructure.channel.SchemaCodec.Encoder;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.spi.Committer;
import org.ddd4j.infrastructure.channel.spi.Writer;
import org.ddd4j.infrastructure.domain.value.ChannelSpec;
import org.ddd4j.infrastructure.publisher.ChannelPublisher;
import org.ddd4j.infrastructure.publisher.FlowSubscription;
import org.ddd4j.infrastructure.publisher.Publisher;
import org.ddd4j.infrastructure.publisher.RevisionCallback;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Key;
import org.ddd4j.value.versioned.CommitResult;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revision;

public class Channels {

	public static final Key<Channels> KEY = Key.of(Channels.class, Channels::new);

	private static final TFunction<CommitResult<?, ?>, Revision> COMMITTER_REVISION = r -> r.foldResult(Committed::getActual,
			c -> Throwing.unchecked(new IllegalStateException(c.toString())));

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
		return context.get(WriteBuffer.POOL).get();
	}

	public <K, V> Committer<K, V> createCommitter(ChannelSpec<K, V> spec) {
		return createCommitter(spec, Encoder.Extender.none());
	}

	public <K, V, T> Committer<K, T> createCommitter(ChannelSpec<K, V> spec, Encoder.Extender<? super T, ? extends V> extender) {
		return encodingCommitter(spec, extender, context.get(Committer.FACTORY).createCommitterClosingBuffers(spec.getName()));
	}

	public <K, V> Committer<K, V> createSubmitter(ChannelSpec<K, V> spec) {
		return createSubmitter(spec, Encoder.Extender.none());
	}

	public <K, V, T> Committer<K, T> createSubmitter(ChannelSpec<K, V> spec, Encoder.Extender<? super T, ? extends V> extender) {
		Committer<ReadBuffer, ReadBuffer> committer = context.get(Committer.FACTORY).createCommitter(spec.getName());
		Writer<ReadBuffer, ReadBuffer> writer = context.get(Writer.FACTORY).createWriter(spec.getName());
		Committer<ReadBuffer, ReadBuffer> submitter = attempt -> committer.commit(withBuffers(attempt, ReadBuffer::mark))
				.thenRun(() -> withBuffers(attempt, ReadBuffer::reset))
				// compiler's type inference failed
				.thenCompose(r -> r.<Promise<? extends CommitResult<ReadBuffer, ReadBuffer>>>onCommitted(writer::put, Promise.completed(r)))
				.thenRun(() -> withBuffers(attempt, ReadBuffer::reset));
		return encodingCommitter(spec, extender, submitter);
	}

	public <K, V> Writer<K, V> createWriter(ChannelSpec<K, V> spec) {
		return createWriter(spec, Encoder.Extender.none());
	}

	public <K, V, T> Writer<K, T> createWriter(ChannelSpec<K, V> spec, Encoder.Extender<? super T, ? extends V> extender) {
		return encodingWriter(spec, extender, context.get(Writer.FACTORY).createWriterClosingBuffers(spec.getName()));
	}

	public <K, V, T> Committer<K, T> encodingCommitter(ChannelSpec<K, V> spec, Encoder.Extender<? super T, ? extends V> extender,
			Committer<ReadBuffer, ReadBuffer> committer) {
		Encoder<T> encoder = context.get(SchemaCodec.FACTORY).encoder(spec.getValueType(), spec.getName()).extend(extender);
		return committer.flatMapValue(k -> buf().accept(b -> spec.serializeKey(k, b)).flip(),
				(v, p) -> encoder.encode(buf(), p.thenApply(COMMITTER_REVISION), v).thenApply(WriteBuffer::flip));
	}

	public <K, V, T> Writer<K, T> encodingWriter(ChannelSpec<K, V> spec, Encoder.Extender<? super T, ? extends V> extender,
			Writer<ReadBuffer, ReadBuffer> writer) {
		Encoder<T> encoder = context.get(SchemaCodec.FACTORY).encoder(spec.getValueType(), spec.getName()).extend(extender);
		return writer.flatMapValue(k -> buf().accept(b -> spec.serializeKey(k, b)).flip(),
				(v, p) -> encoder.encode(buf(), p.thenApply(Committed::getActual), v).thenApply(WriteBuffer::flip));
	}

	public <K, V> Publisher<K, V, RevisionCallback> publisher(ChannelSpec<K, V> spec) {
		ChannelPublisher<RevisionCallback> publisher = context.get(RevisionCallback.PUBLISHER);
		Consumer<Object> unsubscriber = publisher.unsubscriber(spec.getName());
		DecodingFactory<K, V> decodingFactory = context.get(SchemaCodec.FACTORY).decodingFactory(spec);
		// TODO
		return (s, c) -> {
			publisher.subscribe(spec.getName(), s, FlowSubscription.createListener(listenerFactory, c, s, unsubscriber, decodingFactory));
		};
	}

	public <K, V> void subscribe(ChannelSpec<K, V> spec, CommitListener<K, V> commit, ErrorListener error, RevisionCallback callback) {
		CommitListener<ReadBuffer, ReadBuffer> decoded = context.get(SchemaCodec.FACTORY).decodingFactory(spec).create(commit, error);
		context.get(RevisionCallback.PUBLISHER).subscribe(spec.getName(), commit, decoded, error, callback);
	}
}
