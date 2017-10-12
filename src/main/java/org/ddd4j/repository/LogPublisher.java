package org.ddd4j.repository;

import org.ddd4j.Require;
import org.ddd4j.collection.Lazy;
import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.api.CompletionListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.RepartitioningListener;
import org.ddd4j.infrastructure.channel.api.SourceListener;
import org.ddd4j.infrastructure.channel.spi.ColdSource;
import org.ddd4j.infrastructure.channel.spi.HotSource;
import org.ddd4j.infrastructure.domain.ChannelRevisions;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelPartition;
import org.ddd4j.infrastructure.domain.value.ChannelRevision;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Position;

public class LogPublisher implements SourceListener<ReadBuffer, ReadBuffer> {

	public interface RevisionCallback {

		Promise<Sequence<ChannelRevision>> loadRevisions(Sequence<ChannelPartition> partitions);

		Promise<?> saveRevisions(Sequence<ChannelRevision> revisions);
	}

	private class RevisionAwareListener
			implements SourceListener<ReadBuffer, ReadBuffer>, CompletionListener, ErrorListener, RepartitioningListener {

		private final SourceListener<ReadBuffer, ReadBuffer> source;
		private final ErrorListener error;
		private final RevisionCallback callback;
		private final ChannelRevisions state;
		private final Lazy<ColdSource> coldSource;

		public RevisionAwareListener(SourceListener<ReadBuffer, ReadBuffer> source, ErrorListener error, RevisionCallback callback) {
			this.source = Require.nonNull(source);
			this.error = Require.nonNull(error);
			this.callback = Require.nonNull(callback);
			this.state = new ChannelRevisions();
			this.coldSource = Lazy.ofCloseable(() -> coldFactory.createColdSource(this, this, this));
		}

		@Override
		public void onComplete() {
			coldSource.destroy();
		}

		@Override
		public void onError(Throwable throwable) {
			error.onError(throwable);
		}

		@Override
		public void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
			Position position = state.tryUpdate(name, committed);
			if (position == Position.UPTODATE) {
				source.onNext(name, committed);
			} else if (position == Position.BEHIND) {
				coldSource.get().resume(state.revision(name, committed));
			}
		}

		@Override
		public Promise<?> onPartitionsAssigned(Sequence<ChannelPartition> partitions) {
			return callback.loadRevisions(partitions)
					.whenCompleteSuccessfully(state::add)
					.whenCompleteSuccessfully(this::resumeIfNecessary)
					.whenCompleteExceptionally(error::onError);
		}

		@Override
		public Promise<?> onPartitionsRevoked(Sequence<ChannelPartition> partitions) {
			return callback.saveRevisions(state.remove(partitions)).whenCompleteExceptionally(error::onError);
		}

		private void resumeIfNecessary(Sequence<ChannelRevision> revisions) {
			if (!hotState.contains(revisions)) {
				coldSource.get().resume(revisions);
			}
		}
	}

	private final ColdSource.Factory coldFactory;
	private final ChannelPublisher publisher;
	private final ChannelRevisions hotState;
	private final HotSource hotSource;

	public LogPublisher(Scheduler scheduler, ColdSource.Factory coldFactory, HotSource.Factory hotFactory) {
		this.coldFactory = Require.nonNull(coldFactory);
		this.publisher = new ChannelPublisher(scheduler, this::onSubscribed, this::onUnsubscribed);
		this.hotState = new ChannelRevisions();
		this.hotSource = hotFactory.createHotSource(publisher, publisher, publisher);
	}

	@Override
	public void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
		hotState.tryUpdate(name, committed);
	}

	private Promise<Integer> onSubscribed(ChannelName name) {
		publisher.subscribe(name, this, CompletionListener.VOID, ErrorListener.VOID, RepartitioningListener.VOID);
		return hotSource.subscribe(name);
	}

	private void onUnsubscribed(ChannelName name) {
		publisher.unsubscribe(name, this);
		hotSource.unsubscribe(name);
	}

	public void subscribe(ChannelName name, SourceListener<ReadBuffer, ReadBuffer> source, ErrorListener error, RevisionCallback callback) {
		// TODO override ChannelPublisher
		RevisionAwareListener listener = new RevisionAwareListener(source, error, callback);
		publisher.subscribe(name, source, listener, listener, listener, listener);
	}
}