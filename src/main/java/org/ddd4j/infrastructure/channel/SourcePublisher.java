package org.ddd4j.infrastructure.channel;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.CompletionListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.spi.ColdSource;
import org.ddd4j.infrastructure.channel.spi.HotSource;
import org.ddd4j.infrastructure.domain.ChannelRevisions;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelPartition;
import org.ddd4j.infrastructure.domain.value.ChannelRevision;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.repository.log.LogPublisher;
import org.ddd4j.repository.log.LogPublisher.Listener;
import org.ddd4j.util.Lazy;
import org.ddd4j.util.Sequence;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Position;

public class SourcePublisher implements CommitListener<ReadBuffer, ReadBuffer> {

	public interface RevisionCallback {

		RevisionCallback VOID = new RevisionCallback() {

			@Override
			public Promise<Sequence<ChannelRevision>> loadRevisions(Sequence<ChannelPartition> partitions) {
				return Promise.completed(Sequence.empty());
			}

			@Override
			public Promise<?> saveRevisions(Sequence<ChannelRevision> revisions) {
				return Promise.completed();
			}
		};

		Promise<Sequence<ChannelRevision>> loadRevisions(Sequence<ChannelPartition> partitions);

		Promise<?> saveRevisions(Sequence<ChannelRevision> revisions);
	}

	private class RevisionAwareListener implements Listener, CompletionListener {

		private final CommitListener<ReadBuffer, ReadBuffer> commit;
		private final ErrorListener error;
		private final RevisionCallback callback;
		private final ChannelRevisions state;
		private final Lazy<ColdSource> coldSource;

		public RevisionAwareListener(CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error, RevisionCallback callback) {
			this.commit = Require.nonNull(commit);
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
				commit.onNext(name, committed);
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
	private final LogPublisher<RevisionCallback> publisher;
	private final ChannelRevisions hotState;
	private final HotSource hotSource;

	public SourcePublisher(ColdSource.Factory coldFactory, HotSource.Factory hotFactory) {
		this.coldFactory = Require.nonNull(coldFactory);
		this.publisher = new LogPublisher<>(RevisionAwareListener::new, this::onSubscribed, this::onUnsubscribed);
		this.hotState = new ChannelRevisions();
		this.hotSource = hotFactory.createHotSource(publisher, publisher, publisher);
	}

	@Override
	public void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
		hotState.tryUpdate(name, committed);
	}

	private Promise<Integer> onSubscribed(ChannelName name) {
		publisher.subscribe(name, this, ErrorListener.VOID, RevisionCallback.VOID);
		return hotSource.subscribe(name);
	}

	private void onUnsubscribed(ChannelName name) {
		publisher.unsubscribe(name, this);
		hotSource.unsubscribe(name);
	}
}