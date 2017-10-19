package org.ddd4j.infrastructure.channel.spi;

import java.time.Instant;
import java.util.Map;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
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
import org.ddd4j.spi.Key;
import org.ddd4j.util.Sequence;
import org.ddd4j.value.versioned.Committed;

public interface ColdSource extends TimeIndexed, Throwing.Closeable {

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
		public void onComplete() {
			state.clear();
			checkCompleteness();
		}

		@Override
		public void onError(Throwable throwable) {
			error.onError(throwable);
		}

		@Override
		public void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
			commit.onNext(name, committed);
			state.tryUpdate(name, committed);
		}

		@Override
		public Promise<ChannelRevision> revision(ChannelPartition partition, Instant timestamp, Direction direction) {
			return delegate.revision(partition, timestamp, direction);
		}

		@Override
		public void start(Sequence<ChannelRevision> revisions) {
			state.add(revisions);
			delegate.start(revisions);
		}

		@Override
		public void stop(Sequence<ChannelPartition> partitions) {
			state.remove(partitions);
			delegate.stop(partitions);
			checkCompleteness();
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
			public void closeChecked() throws Exception {
				context.get(ColdReader.FACTORY).closeChecked();
			}

			@Override
			public ColdSource createColdSource(CommitListener<ReadBuffer, ReadBuffer> commit, CompletionListener completion,
					ErrorListener error) {
				Scheduler scheduler = context.get(Scheduler.KEY);
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

		public ColdReaderBased(Scheduler scheduler, ColdReader delegate, CommitListener<ReadBuffer, ReadBuffer> commit,
				CompletionListener completion, ErrorListener error) {
			this.commit = Require.nonNull(commit);
			this.completion = Require.nonNull(completion);
			this.error = Require.nonNull(error);
			this.delegate = Require.nonNull(delegate);
			this.rescheduler = scheduler.reschedulerFor(this);
			this.state = new ChannelRevisions();
		}

		@Override
		public void closeChecked() {
			state.clear();
		}

		@Override
		public Promise<Trigger> onScheduled(Scheduler scheduler) {
			return delegate.get(state)
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
		public void start(Sequence<ChannelRevision> revisions) {
			state.add(revisions);
			rescheduler.doIfNecessary();
		}

		@Override
		public void stop(Sequence<ChannelPartition> partitions) {
			state.remove(partitions);
		}
	}

	interface Factory extends DataAccessFactory {

		default ColdSource createAutoClosingColdSource(CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error) {
			return new AutoClosing(this, commit, error);
		}

		ColdSource createColdSource(CommitListener<ReadBuffer, ReadBuffer> commit, CompletionListener completion, ErrorListener error);
	}

	Key<Factory> FACTORY = Key.of(Factory.class, ColdReaderBased.Factory::new);

	void start(Sequence<ChannelRevision> revisions);

	void stop(Sequence<ChannelPartition> partitions);
}
