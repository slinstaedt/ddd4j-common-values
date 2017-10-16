package org.ddd4j.repository.command;

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
import org.ddd4j.repository.event.ChannelListener;
import org.ddd4j.repository.event.MessageDispatcher;
import org.ddd4j.repository.event.SubscribedChannels;
import org.ddd4j.spi.Key;
import org.ddd4j.util.Lazy;
import org.ddd4j.util.Sequence;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Position;

public interface CommandDispatcher {

	class ChannelPublisher {

		private class RevisionAwareListener implements ChannelListener, CompletionListener {

			private final CommitListener<ReadBuffer, ReadBuffer> commit;
			private final ErrorListener error;
			private final CommandDispatcher callback;
			private final ChannelRevisions state;
			private final Lazy<ColdSource> coldSource;

			public RevisionAwareListener(CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error, CommandDispatcher callback) {
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
		private final MessageDispatcher<CommandDispatcher> dispatcher;
		private final ChannelRevisions hotState;
		private final HotSource hotSource;

		public ChannelPublisher(ColdSource.Factory coldFactory, HotSource.Factory hotFactory) {
			this.coldFactory = Require.nonNull(coldFactory);
			SubscribedChannels channels = new SubscribedChannels(this::onSubscribed, this::onUnsubscribed);
			this.dispatcher = new MessageDispatcher<>(channels, RevisionAwareListener::new);
			this.hotState = new ChannelRevisions();
			this.hotSource = hotFactory.createHotSource(channels, channels, channels);
		}

		public MessageDispatcher<CommandDispatcher> getPublisher() {
			return dispatcher;
		}

		private Promise<Integer> onSubscribed(ChannelName name) {
			dispatcher.subscribe(name, this, hotState::tryUpdate, ErrorListener.VOID, CommandDispatcher.VOID);
			return hotSource.subscribe(name);
		}

		private void onUnsubscribed(ChannelName name) {
			dispatcher.unsubscribe(name, this);
			hotSource.unsubscribe(name);
		}
	}

	Key<MessageDispatcher<CommandDispatcher>> PUBLISHER = Key.of("",
			ctx -> new ChannelPublisher(ctx.get(ColdSource.FACTORY), ctx.get(HotSource.FACTORY)).getPublisher());

	CommandDispatcher VOID = new CommandDispatcher() {

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