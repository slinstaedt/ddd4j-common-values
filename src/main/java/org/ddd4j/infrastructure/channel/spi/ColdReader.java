package org.ddd4j.infrastructure.channel.spi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.Promise.Cancelable;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.CompletionListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelRevision;
import org.ddd4j.infrastructure.domain.value.CommittedRecords;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Key;
import org.ddd4j.util.Sequence;
import org.ddd4j.value.config.ConfKey;
import org.ddd4j.value.config.Configuration;
import org.ddd4j.value.versioned.Committed;

public interface ColdReader {

	class ColdSourceBased implements ColdReader {

		public static class Factory implements ColdReader.Factory {

			public static final ConfKey<Integer> TIMEOUT = Configuration.keyOfInteger("timeoutInMillis", 2000);

			private final Context context;

			public Factory(Context context) {
				this.context = Require.nonNull(context);
				Require.that(context.get(ColdSource.FACTORY), ColdSource.ColdReaderBased.Factory.class::isInstance);
			}

			@Override
			public void closeChecked() throws Exception {
				context.get(ColdSource.FACTORY).closeChecked();
			}

			@Override
			public ColdReader createColdReader() {
				return new ColdSourceBased(context.get(Scheduler.KEY), context.get(ColdSource.FACTORY), context.conf(TIMEOUT));
			}

			@Override
			public Map<ChannelName, Integer> knownChannelNames() {
				return context.get(ColdSource.FACTORY).knownChannelNames();
			}
		}

		private static class Listener implements CommitListener<ReadBuffer, ReadBuffer>, ErrorListener, CompletionListener {

			private final Promise.Deferred<CommittedRecords> deferred;
			private final ColdSource source;
			private final Map<ChannelName, List<Committed<ReadBuffer, ReadBuffer>>> records;
			private final Supplier<Promise.Cancelable<?>> timerProvider;
			private Cancelable<?> timer;

			Listener(Scheduler scheduler, ColdSource.Factory delegate, Sequence<ChannelRevision> revisions, int timeoutInMillis) {
				this.deferred = scheduler.createDeferredPromise();
				this.source = delegate.createColdSource(this, this, this);
				this.records = new HashMap<>();
				this.timerProvider = () -> scheduler.schedule(this::timeout, timeoutInMillis, TimeUnit.MILLISECONDS);
				source.resume(revisions);
				timer = timerProvider.get();
			}

			Promise<CommittedRecords> getResult() {
				return deferred;
			}

			@Override
			public void onComplete() {
				deferred.completeSuccessfully(CommittedRecords.copied(records));
			}

			@Override
			public void onError(Throwable throwable) {
				deferred.completeExceptionally(throwable);
			}

			@Override
			public void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
				if (deferred.isDone()) {
					source.close();
				} else {
					records.computeIfAbsent(name, r -> new ArrayList<>()).add(committed);
					timer.cancel();
					timer = timerProvider.get();
				}
			}

			void timeout() {
				source.close();
				deferred.completeSuccessfully(CommittedRecords.copied(records));
			}
		}

		private final Scheduler scheduler;
		private final ColdSource.Factory delegate;
		private final int timeoutInMillis;

		public ColdSourceBased(Scheduler scheduler, ColdSource.Factory delegate, int timeoutInMillis) {
			this.scheduler = Require.nonNull(scheduler);
			this.delegate = Require.nonNull(delegate);
			this.timeoutInMillis = timeoutInMillis;
		}

		@Override
		public Promise<CommittedRecords> get(Sequence<ChannelRevision> revisions) {
			return new Listener(scheduler, delegate, revisions, timeoutInMillis).getResult();
		}
	}

	interface Factory extends DataAccessFactory {

		ColdReader createColdReader();
	}

	Key<Factory> FACTORY = Key.of(Factory.class, ColdSourceBased.Factory::new);

	default Promise<CommittedRecords> get(ChannelRevision... revisions) {
		return get(Sequence.of(revisions));
	}

	Promise<CommittedRecords> get(Sequence<ChannelRevision> revisions);

	default Promise<Committed<ReadBuffer, ReadBuffer>> getCommitted(ChannelRevision revision) {
		return get(revision).thenApply(cr -> cr.commit(revision));
	}

	default Promise<ReadBuffer> getCommittedValue(ChannelRevision revision) {
		return getCommitted(revision).thenApply(Committed::getValue);
	}
}
