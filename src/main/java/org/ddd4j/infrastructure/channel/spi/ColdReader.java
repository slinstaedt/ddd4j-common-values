package org.ddd4j.infrastructure.channel.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.ddd4j.Require;
import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.Promise.Cancelable;
import org.ddd4j.infrastructure.channel.api.SourceListener;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.infrastructure.channel.domain.ChannelRevision;
import org.ddd4j.infrastructure.channel.spi.ColdSource.Callback;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Key;
import org.ddd4j.value.config.ConfKey;
import org.ddd4j.value.config.Configuration;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;

public interface ColdReader {

	interface Factory extends DataAccessFactory {

		ColdReader createColdReader();
	}

	class ColdSourceBased implements ColdReader {

		public static class Factory implements ColdReader.Factory {

			public static final ConfKey<Integer> TIMEOUT = Configuration.keyOfInteger("timeoutInMillis", 2000);

			private final Context context;

			public Factory(Context context) {
				this.context = Require.nonNull(context);
				Require.that(context.get(ColdSource.FACTORY), ColdSource.VersionedReaderBased.Factory.class::isInstance);
			}

			@Override
			public ColdReader createColdReader() {
				return new ColdSourceBased(context.get(Scheduler.KEY), context.get(ColdSource.FACTORY), context.conf(TIMEOUT));
			}

			@Override
			public void closeChecked() throws Exception {
				context.get(ColdSource.FACTORY).closeChecked();
			}

			@Override
			public Map<ChannelName, Integer> knownChannelNames() {
				return context.get(ColdSource.FACTORY).knownChannelNames();
			}
		}

		private static class Listener implements Callback, SourceListener<ReadBuffer, ReadBuffer> {

			private final Promise.Deferred<CommittedRecords> deferred;
			private final ColdSource source;
			private final Map<ChannelName, List<Committed<ReadBuffer, ReadBuffer>>> records;
			private final Supplier<Promise.Cancelable<?>> timerProvider;
			private Cancelable<?> timer;

			Listener(Scheduler scheduler, ColdSource.Factory delegate, Sequence<ChannelRevision> revisions, int timeoutInMillis) {
				this.deferred = scheduler.createDeferredPromise();
				this.source = delegate.createColdSource(this, this);
				this.records = new HashMap<>();
				this.timerProvider = () -> scheduler.schedule(this::timeout, timeoutInMillis, TimeUnit.MILLISECONDS);
				source.resume(revisions);
				timer = timerProvider.get();
			}

			void timeout() {
				source.close();
				deferred.completeSuccessfully(CommittedRecords.copied(records));
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

	class CommittedRecords {

		public static final CommittedRecords EMPTY = new CommittedRecords(Collections.emptyMap());

		public static CommittedRecords copied(Map<ChannelName, List<Committed<ReadBuffer, ReadBuffer>>> values) {
			Map<ChannelName, Sequence<Committed<ReadBuffer, ReadBuffer>>> copy = new HashMap<>();
			values.forEach((r, c) -> copy.put(r, Sequence.ofCopied(c)));
			return new CommittedRecords(copy);
		}

		private final Map<ChannelName, Sequence<Committed<ReadBuffer, ReadBuffer>>> values;

		public CommittedRecords(Map<ChannelName, Sequence<Committed<ReadBuffer, ReadBuffer>>> values) {
			this.values = new HashMap<>(values);
		}

		public Committed<ReadBuffer, ReadBuffer> commit(ChannelName name, Revision actual) {
			return commits(name).filter(c -> c.getActual().equal(actual)).head().orElseThrow(NoSuchElementException::new);
		}

		public Committed<ReadBuffer, ReadBuffer> commit(ChannelRevision spec) {
			return commits(spec.getName()).filter(c -> c.getActual().equal(spec.getRevision()))
					.head()
					.orElseThrow(NoSuchElementException::new);
		}

		public Sequence<Committed<ReadBuffer, ReadBuffer>> commits(ChannelName name) {
			return values.getOrDefault(name, Sequence.empty());
		}

		public boolean isNotEmpty() {
			return !values.isEmpty();
		}

		public void forEach(BiConsumer<ChannelName, Committed<ReadBuffer, ReadBuffer>> consumer) {
			values.forEach((r, s) -> s.forEach(c -> consumer.accept(r, c)));
		}

		public void forEachOrEmpty(BiConsumer<ChannelName, Committed<ReadBuffer, ReadBuffer>> consumer, Runnable empty) {
			if (values.isEmpty()) {
				empty.run();
			} else {
				forEach(consumer);
			}
		}
	}

	Key<Factory> FACTORY = Key.of(Factory.class, ColdSourceBased.Factory::new);

	Promise<CommittedRecords> get(Sequence<ChannelRevision> revisions);

	default Promise<CommittedRecords> get(ChannelRevision... revisions) {
		return get(Sequence.of(revisions));
	}

	default Promise<Committed<ReadBuffer, ReadBuffer>> getCommitted(ChannelRevision revision) {
		return get(revision).thenApply(cr -> cr.commit(revision));
	}

	default Promise<ReadBuffer> getCommittedValue(ChannelRevision revision) {
		return getCommitted(revision).thenApply(Committed::getValue);
	}
}
