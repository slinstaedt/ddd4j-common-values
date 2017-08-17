package org.ddd4j.infrastructure.channel;

import java.util.Map;

import org.ddd4j.Require;
import org.ddd4j.Throwing;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.ChannelName;
import org.ddd4j.infrastructure.ChannelRevision;
import org.ddd4j.infrastructure.channel.util.ChannelRevisions;
import org.ddd4j.infrastructure.channel.util.SourceListener;
import org.ddd4j.infrastructure.scheduler.ScheduledTask;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Key;
import org.ddd4j.value.collection.Seq;

public interface ColdSource extends Throwing.Closeable {

	interface Callback {

		void onComplete();

		void onError(Throwable throwable);
	}

	interface Factory extends DataAccessFactory {

		ColdSource createColdSource(Callback callback, SourceListener<ReadBuffer, ReadBuffer> listener);
	}

	class VersionedReaderBased implements ColdSource, ScheduledTask {

		public static class Factory implements ColdSource.Factory {

			private final Context context;

			public Factory(Context context) {
				this.context = Require.nonNull(context);
				Require.that(context.get(ColdSource.FACTORY), ColdReader.ColdSourceBased.Factory.class::isInstance);
			}

			@Override
			public ColdSource createColdSource(Callback callback, SourceListener<ReadBuffer, ReadBuffer> listener) {
				ColdReader reader = context.get(ColdReader.FACTORY).createVersionedReader();
				Scheduler scheduler = context.get(Scheduler.KEY);
				return new VersionedReaderBased(callback, listener, reader, scheduler);
			}

			@Override
			public void closeChecked() throws Exception {
				context.get(ColdReader.FACTORY).closeChecked();
			}

			@Override
			public Map<ChannelName, Integer> knownResources() {
				return context.get(ColdReader.FACTORY).knownResources();
			}
		}

		private final Callback callback;
		private final SourceListener<ReadBuffer, ReadBuffer> listener;
		private final ColdReader reader;
		private final Rescheduler rescheduler;
		private final ChannelRevisions channelRevisions;

		public VersionedReaderBased(Callback callback, SourceListener<ReadBuffer, ReadBuffer> listener, ColdReader reader,
				Scheduler scheduler) {
			this.callback = Require.nonNull(callback);
			this.listener = Require.nonNull(listener);
			this.reader = Require.nonNull(reader);
			this.rescheduler = scheduler.reschedulerFor(this);
			this.channelRevisions = new ChannelRevisions();
		}

		@Override
		public void closeChecked() {
			channelRevisions.clear();
		}

		@Override
		public Promise<Trigger> onScheduled(Scheduler scheduler) {
			return reader.get(channelRevisions)
					.whenComplete(rc -> rc.forEachOrEmpty(listener::onNext, callback::onComplete), callback::onError)
					.thenApply(rc -> channelRevisions.isNotEmpty() && rc.isNotEmpty() ? Trigger.RESCHEDULE : Trigger.NOTHING);
		}

		@Override
		public void pause(Seq<ChannelRevision> revisions) {
			channelRevisions.remove(revisions);
		}

		@Override
		public void resume(Seq<ChannelRevision> revisions) {
			channelRevisions.add(revisions);
			rescheduler.doIfNecessary();
		}
	}

	Key<Factory> FACTORY = Key.of(Factory.class, VersionedReaderBased.Factory::new);

	void pause(Seq<ChannelRevision> revisions);

	void resume(Seq<ChannelRevision> revisions);
}
