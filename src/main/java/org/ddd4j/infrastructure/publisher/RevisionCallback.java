package org.ddd4j.infrastructure.publisher;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.CompletionListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.RebalanceListener;
import org.ddd4j.infrastructure.channel.spi.ColdSource;
import org.ddd4j.infrastructure.channel.spi.FlowControlled;
import org.ddd4j.infrastructure.channel.spi.HotSource;
import org.ddd4j.infrastructure.domain.ChannelRevisions;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelPartition;
import org.ddd4j.infrastructure.domain.value.ChannelRevision;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Ref;
import org.ddd4j.util.Lazy;
import org.ddd4j.util.Require;
import org.ddd4j.util.value.Sequence;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Position;

public interface RevisionCallback {

	class SubscribedChannelsListener implements SubscribedChannels.Listener {

		private class RevisionAwareListener implements ChannelListener, CompletionListener {

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
			public void closeChecked() throws Exception {
				coldSource.destroy();
				callback.saveRevisions(state).whenCompleteExceptionally(error::onError);
			}

			@Override
			public Promise<?> controlFlow(FlowControlled.Mode mode, Sequence<Void> values) {
				return coldSource.ifPresent(s -> s.controlFlow(mode, state.partitions()), Promise.completed());
			}

			@Override
			public Promise<?> onComplete() {
				return Promise.completed().whenComplete(coldSource::destroy);
			}

			@Override
			public Promise<?> onError(Throwable throwable) {
				return error.onError(throwable);
			}

			@Override
			public Promise<?> onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
				Promise<?> result = Promise.completed();
				Position position = state.tryUpdate(name, committed);
				if (position == Position.UPTODATE) {
					result = commit.onNext(name, committed);
				} else if (position == Position.BEHIND) {
					coldSource.get().start(state.revision(name, committed));
				}
				return result;
			}

			@Override
			public Promise<?> onRebalance(RebalanceListener.Mode mode, Sequence<ChannelPartition> partitions) {
				switch (mode) {
				case ASSIGNED:
					return callback.loadRevisions(partitions)
							.whenCompleteSuccessfully(state::add)
							.whenCompleteSuccessfully(this::resumeIfNecessary)
							.whenCompleteExceptionally(error::onError);
				case REVOKED:
					return callback.saveRevisions(state.remove(partitions)).whenCompleteExceptionally(error::onError);
				default:
					throw new AssertionError(mode);
				}
			}

			private void resumeIfNecessary(Sequence<ChannelRevision> revisions) {
				if (!hotState.contains(revisions)) {
					coldSource.get().start(revisions);
				}
			}
		}

		private final ColdSource.Factory coldFactory;
		private final ChannelPublisher<RevisionCallback> publisher;
		private final ChannelRevisions hotState;
		private final HotSource hotSource;

		public SubscribedChannelsListener(ColdSource.Factory coldFactory, HotSource.Factory hotFactory) {
			SubscribedChannels channels = new SubscribedChannels(this);
			this.coldFactory = Require.nonNull(coldFactory);
			this.publisher = new ChannelPublisher<>(channels, RevisionAwareListener::new);
			this.hotState = new ChannelRevisions();
			this.hotSource = hotFactory.createHotSource(channels, channels, channels);
		}

		public ChannelPublisher<RevisionCallback> getPublisher() {
			return publisher;
		}

		private Promise<?> hotUpdate(ChannelName name, Committed<?, ?> committed) {
			hotState.tryUpdate(name, committed);
			return Promise.completed();
		}

		@Override
		public Promise<Integer> onSubscribed(ChannelName name) {
			publisher.subscribe(name, this, this::hotUpdate, ErrorListener.IGNORE, RevisionCallback.VOID);
			return hotSource.subscribe(name);
		}

		@Override
		public void onUnsubscribed(ChannelName name) {
			publisher.unsubscribe(name, this);
			hotSource.unsubscribe(name);
		}
	}

	Ref<ChannelPublisher<RevisionCallback>> PUBLISHER = Ref.of("revisionCallbackChannelPublisher",
			ctx -> new SubscribedChannelsListener(ctx.get(ColdSource.FACTORY), ctx.get(HotSource.FACTORY)).getPublisher());

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