package org.ddd4j.infrastructure.channel.spi;

import java.time.Instant;
import java.util.Map;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.CompletionListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.domain.ChannelRevisions;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelPartition;
import org.ddd4j.infrastructure.domain.value.ChannelRevision;
import org.ddd4j.infrastructure.scheduler.ScheduledTask;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Ref;
import org.ddd4j.util.Require;
import org.ddd4j.util.Throwing;
import org.ddd4j.util.collection.Array;
import org.ddd4j.util.value.Sequence;
import org.ddd4j.value.versioned.Committed;

public interface ColdSource extends FlowControlled<ChannelPartition>, TimeIndexed, Throwing.Closeable {

	class AutoClosing implements ColdSource, CommitListener<ReadBuffer, ReadBuffer>, CompletionListener, ErrorListener {

		private final CommitListener<ReadBuffer, ReadBuffer> commit;
		private final ErrorListener error;
		private final ChannelRevisions state;
		private final ColdSource delegate;

		public AutoClosing(Factory factory, CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error) {
			this.commit = Require.nonNull(commit);
			this.error = Require.nonNull(error);
			this.state = new ChannelRevisions();
			this.delegate = factory.createColdSource(this, this, this);
		}

		private void checkCompleteness() {
			if (state.isEmpty()) {
				delegate.close();
			}
		}

		@Override
		public void closeChecked() throws Exception {
			state.clear();
			delegate.closeChecked();
		}

		@Override
		public Promise<?> controlFlow(Mode mode, Sequence<ChannelPartition> values) {
			return delegate.controlFlow(mode, values);
		}

		@Override
		public Promise<?> onComplete() {
			state.clear();
			checkCompleteness();
			return Promise.completed();
		}

		@Override
		public Promise<?> onError(Throwable throwable) {
			return error.onError(throwable);
		}

		@Override
		public Promise<?> onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
			return commit.onNext(name, committed).thenApply(v -> state.tryUpdate(name, committed));
		}

		@Override
		public Promise<ChannelRevision> revision(ChannelPartition partition, Instant timestamp, Direction direction) {
			return delegate.revision(partition, timestamp, direction);
		}

		@Override
		public Promise<?> start(Sequence<ChannelRevision> revisions) {
			state.add(revisions);
			return delegate.start(revisions);
		}

		@Override
		public Promise<?> stop(Sequence<ChannelPartition> partitions) {
			state.remove(partitions);
			return delegate.stop(partitions).thenRun(this::checkCompleteness);
		}
	}

	class ColdReaderBased implements ColdSource, ScheduledTask {

		public static class Factory implements ColdSource.Factory {

			private final Context context;

			public Factory(Context context) {
				this.context = Require.nonNull(context);
				Require.that(context.get(ColdSource.FACTORY), ColdReader.ColdSourceBased.Factory.class::isInstance);
			}

			@Override
			public ColdSource createColdSource(CommitListener<ReadBuffer, ReadBuffer> commit, CompletionListener completion,
					ErrorListener error) {
				Scheduler scheduler = context.get(Scheduler.REF);
				ColdReader reader = context.get(ColdReader.FACTORY).createColdReader();
				return new ColdReaderBased(scheduler, reader, commit, completion, error);
			}

			@Override
			public Map<ChannelName, Integer> knownChannelNames() {
				return context.get(ColdReader.FACTORY).knownChannelNames();
			}
		}

		private final CommitListener<ReadBuffer, ReadBuffer> commit;
		private final CompletionListener completion;
		private final ErrorListener error;
		private final ColdReader delegate;
		private final Rescheduler rescheduler;
		private final ChannelRevisions state;
		private final Array<ChannelPartition> paused;

		public ColdReaderBased(Scheduler scheduler, ColdReader delegate, CommitListener<ReadBuffer, ReadBuffer> commit,
				CompletionListener completion, ErrorListener error) {
			this.commit = Require.nonNull(commit);
			this.completion = Require.nonNull(completion);
			this.error = Require.nonNull(error);
			this.delegate = Require.nonNull(delegate);
			this.rescheduler = scheduler.reschedulerFor(this);
			this.state = new ChannelRevisions();
			this.paused = new Array<>();
		}

		@Override
		public void closeChecked() {
			state.clear();
		}

		@Override
		public Promise<?> controlFlow(Mode mode, Sequence<ChannelPartition> values) {
			switch (mode) {
			case PAUSE:
				paused.addAll(values);
				break;
			case RESUME:
				paused.removeAll(values);
				break;
			}
			return Promise.completed();
		}

		@Override
		public Promise<Trigger> onScheduled(Scheduler scheduler) {
			return delegate.get(state.without(paused))
					.whenCompleteSuccessfully(cr -> cr.forEach(state::tryUpdate))
					.whenCompleteSuccessfully(cr -> cr.forEachOrEmpty(commit::onNext, completion::onComplete))
					.whenCompleteExceptionally(error::onError)
					.thenApply(rc -> state.isNotEmpty() && rc.isNotEmpty() ? Trigger.RESCHEDULE : Trigger.NOTHING);
		}

		@Override
		public Promise<ChannelRevision> revision(ChannelPartition partition, Instant timestamp, Direction direction) {
			return delegate.revision(partition, timestamp, direction);
		}

		@Override
		public Promise<?> start(Sequence<ChannelRevision> revisions) {
			state.add(revisions);
			rescheduler.doIfNecessary();
			return Promise.completed();
		}

		@Override
		public Promise<?> stop(Sequence<ChannelPartition> partitions) {
			state.remove(partitions);
			return Promise.completed();
		}
	}

	interface Factory extends DataAccessFactory {

		default ColdSource createAutoClosingColdSource(CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error) {
			return new AutoClosing(this, commit, error);
		}

		ColdSource createColdSource(CommitListener<ReadBuffer, ReadBuffer> commit, CompletionListener completion, ErrorListener error);
	}

	Ref<Factory> FACTORY = Ref.of(Factory.class, ColdReaderBased.Factory::new);

	Promise<?> start(Sequence<ChannelRevision> revisions);

	Promise<?> stop(Sequence<ChannelPartition> partitions);
}
