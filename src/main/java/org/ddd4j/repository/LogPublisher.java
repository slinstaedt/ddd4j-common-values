package org.ddd4j.repository;

import org.ddd4j.Require;
import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.ChannelRevisions;
import org.ddd4j.infrastructure.channel.SchemaCodec;
import org.ddd4j.infrastructure.channel.api.CompletionListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.RepartitioningListener;
import org.ddd4j.infrastructure.channel.api.SourceListener;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.infrastructure.channel.domain.ChannelPartition;
import org.ddd4j.infrastructure.channel.domain.ChannelRevision;
import org.ddd4j.infrastructure.channel.spi.ColdSource;
import org.ddd4j.infrastructure.channel.spi.HotSource;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;

public class LogPublisher {

	public interface RevisionCallback {

		Promise<Sequence<ChannelRevision>> loadRevisions(Sequence<ChannelPartition> partitions);

		Promise<?> saveRevisions(Sequence<ChannelRevision> revisions);
	}

	private class XXX implements SourceListener<ReadBuffer, ReadBuffer>, ErrorListener, RepartitioningListener {

		private final SourceListener<ReadBuffer, ReadBuffer> source;
		private final ErrorListener error;
		private final RevisionCallback callback;
		private final ChannelRevisions state;

		public XXX(SourceListener<ReadBuffer, ReadBuffer> source, ErrorListener error, RevisionCallback callback) {
			this.source = Require.nonNull(source);
			this.error = Require.nonNull(error);
			this.callback = Require.nonNull(callback);
			this.state = new ChannelRevisions();
		}

		@Override
		public void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
			state.update(name, committed.getNextExpected());
			source.onNext(name, committed);
		}

		@Override
		public Promise<?> onPartitionsAssigned(Sequence<ChannelPartition> partitions) {
			return callback.loadRevisions(partitions).thenAccept(state::add).whenCompleteExceptionally(error::onError);
		}

		@Override
		public Promise<?> onPartitionsRevoked(Sequence<ChannelPartition> partitions) {
			return callback.saveRevisions(state.remove(partitions)).whenCompleteExceptionally(error::onError);
		}

		@Override
		public void onError(Throwable throwable) {
			error.onError(throwable);
		}
	}

	private final ColdSource.Factory coldFactory;
	private final ChannelPublisher publisher;
	private final ChannelRevisions hotState;
	private final HotSource hotSource;

	public LogPublisher(Scheduler scheduler, SchemaCodec.Factory codecFactory, ColdSource.Factory coldFactory,
			HotSource.Factory hotFactory) {
		this.coldFactory = Require.nonNull(coldFactory);
		this.publisher = new ChannelPublisher(scheduler, this::onSubscribed, this::onUnsubscribed);
		this.hotState = new ChannelRevisions();
		this.hotSource = hotFactory.createHotSource(publisher, publisher);
	}

	private Promise<Integer> onSubscribed(ChannelName name) {
		return hotSource.subscribe(name);
	}

	private void onUnsubscribed(ChannelName name) {
		hotSource.unsubscribe(name);
	}

	public void subscribe(ChannelName name, SourceListener<ReadBuffer, ReadBuffer> source, CompletionListener completion,
			ErrorListener error, RevisionCallback callback) {
		XXX listener = new XXX(source, error, callback);
		publisher.subscribe(name, listener, completion, listener, listener);
	}
}