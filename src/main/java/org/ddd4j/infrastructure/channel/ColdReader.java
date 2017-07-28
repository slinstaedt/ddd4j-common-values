package org.ddd4j.infrastructure.channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.ddd4j.Require;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.Promise.Cancelable;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.ResourceRevision;
import org.ddd4j.infrastructure.channel.ColdSource.Callback;
import org.ddd4j.infrastructure.channel.util.SourceListener;
import org.ddd4j.infrastructure.scheduler.Scheduler;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.Key;
import org.ddd4j.value.collection.Configuration;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Committed;

public interface ColdReader {

	interface Factory extends DataAccessFactory {

		ColdReader createVersionedReader();
	}

	class ColdSourceBased implements ColdReader {

		public static class Factory implements ColdReader.Factory {

			private final Context context;

			public Factory(Context context) {
				this.context = Require.nonNull(context);
				Require.that(context.get(ColdSource.FACTORY), ColdSource.VersionedReaderBased.Factory.class::isInstance);
			}

			@Override
			public ColdReader createVersionedReader() {
				return new ColdSourceBased(context.get(Scheduler.KEY), context.get(ColdSource.FACTORY));
			}

			@Override
			public void closeChecked() throws Exception {
				context.get(ColdSource.FACTORY).closeChecked();
			}

			@Override
			public Map<ResourceDescriptor, Integer> knownResources() {
				return context.get(ColdSource.FACTORY).knownResources();
			}
		}

		private static class Listener implements Callback, SourceListener<ReadBuffer, ReadBuffer> {

			public static final Configuration.Key<Integer> TIMEOUT = Configuration.keyOfInteger("timeoutInMillis", 2000);

			private final Promise.Deferred<ResourceRecords> deferred;
			private final ColdSource source;
			private final Map<ResourceDescriptor, List<Committed<ReadBuffer, ReadBuffer>>> records;
			private final Supplier<Promise.Cancelable<?>> timerProvider;
			private Cancelable<?> timer;

			Listener(Scheduler scheduler, ColdSource.Factory delegate, Seq<ResourceRevision> revisions, int timeoutInMillis) {
				this.deferred = scheduler.createDeferredPromise();
				this.source = delegate.createColdSource(this, this);
				this.records = new HashMap<>();
				this.timerProvider = () -> scheduler.schedule(this::timeout, timeoutInMillis, TimeUnit.MILLISECONDS);
				source.resume(revisions);
				timer = timerProvider.get();
			}

			void timeout() {
				source.close();
				deferred.completeSuccessfully(ResourceRecords.copied(records));
			}

			@Override
			public void onNext(ResourceDescriptor resource, Committed<ReadBuffer, ReadBuffer> committed) {
				if (deferred.isDone()) {
					source.close();
				} else {
					records.computeIfAbsent(resource, r -> new ArrayList<>()).add(committed);
					timer.cancel();
					timer = timerProvider.get();
				}
			}

			Promise.Deferred<ResourceRecords> getResult() {
				return deferred;
			}

			@Override
			public void onComplete() {
				deferred.completeSuccessfully(ResourceRecords.copied(records));
			}

			@Override
			public void onError(Throwable throwable) {
				deferred.completeExceptionally(throwable);
			}
		}

		private final Scheduler scheduler;
		private final ColdSource.Factory delegate;

		public ColdSourceBased(Scheduler scheduler, ColdSource.Factory delegate) {
			this.scheduler = Require.nonNull(scheduler);
			this.delegate = Require.nonNull(delegate);
		}

		@Override
		public Promise<ResourceRecords> get(Seq<ResourceRevision> revisions) {
			return new Listener(scheduler, delegate, revisions).getResult();
		}
	}

	class ResourceRecords {

		public static final ResourceRecords EMPTY = new ResourceRecords(Collections.emptyMap());

		public static ResourceRecords copied(Map<ResourceDescriptor, List<Committed<ReadBuffer, ReadBuffer>>> values) {
			Map<ResourceDescriptor, Seq<Committed<ReadBuffer, ReadBuffer>>> copy = new HashMap<>();
			values.forEach((r, c) -> copy.put(r, Seq.ofCopied(c)));
			return new ResourceRecords(copy);
		}

		private final Map<ResourceDescriptor, Seq<Committed<ReadBuffer, ReadBuffer>>> values;

		public ResourceRecords(Map<ResourceDescriptor, Seq<Committed<ReadBuffer, ReadBuffer>>> values) {
			this.values = new HashMap<>(values);
		}

		public Seq<Committed<ReadBuffer, ReadBuffer>> commits(ResourceDescriptor resource) {
			return values.getOrDefault(resource, Seq.empty());
		}

		public boolean isNotEmpty() {
			return !values.isEmpty();
		}

		public void forEach(BiConsumer<ResourceDescriptor, Committed<ReadBuffer, ReadBuffer>> consumer) {
			values.forEach((r, s) -> s.forEach(c -> consumer.accept(r, c)));
		}

		public void forEachOrEmpty(BiConsumer<ResourceDescriptor, Committed<ReadBuffer, ReadBuffer>> consumer, Runnable empty) {
			if (values.isEmpty()) {
				empty.run();
			} else {
				forEach(consumer);
			}
		}
	}

	Key<Factory> FACTORY = Key.of(Factory.class, ColdSourceBased.Factory::new);

	Promise<ResourceRecords> get(Seq<ResourceRevision> revisions);
}
