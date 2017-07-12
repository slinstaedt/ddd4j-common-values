package org.ddd4j.infrastructure.channel;

import java.util.concurrent.Executor;

import org.ddd4j.Throwing;
import org.ddd4j.collection.Array;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.Promise.Deferred;
import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.ResourcePartition;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Key;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;

public interface ColdSource extends Throwing.Closeable {

	interface Factory extends DataAccessFactory {

		ColdSource createColdSource();
	}

	interface Listener<K, V> {

		void onComplete();

		void onError(Throwable throwable);

		void onNext(ResourceDescriptor resource, Committed<K, V> committed);
	}

	interface Pull extends ColdSource {

		@Override
		default void resume(Listener<ReadBuffer, ReadBuffer> listener, ResourceDescriptor resource, Seq<Revision> start) {
			Revisions revisions = new Revisions(start);
			fetch(resource, start).whenComplete(s -> s.forEachOrEmpty(c -> {
				listener.onNext(resource, c);
				revisions.update(c.getNextExpected());
			}, listener::onComplete), listener::onError).checkOrFail(Seq::isNotEmpty).thenRun(() -> resume(listener, resource, revisions));
		}

		@Override
		default void pause(Seq<ResourcePartition> partitions) {
			// TODO Auto-generated method stub
		}
	}

	interface Push extends ColdSource {

		default int batchSize() {
			return 100;
		}

		default Executor executor() {
			return Runnable::run;
		}

		@Override
		default Promise<Seq<Committed<ReadBuffer, ReadBuffer>>> fetch(ResourceDescriptor resource, Seq<Revision> start) {
			Deferred<Seq<Committed<ReadBuffer, ReadBuffer>>> deferred = Promise.deferred(executor());
			Array<Committed<ReadBuffer, ReadBuffer>> result = new Array<>();
			resume(new ColdSource.Listener<ReadBuffer, ReadBuffer>() {

				@Override
				public void onComplete() {
					deferred.completeSuccessfully(result::stream);
				}

				@Override
				public void onError(Throwable throwable) {
					deferred.completeExceptionally(throwable);
				}

				@Override
				public void onNext(ResourceDescriptor resource, Committed<ReadBuffer, ReadBuffer> committed) {
					result.add(committed);
					if (result.size() >= batchSize()) {
						onComplete();
						pause(null);
					}
				}
			}, resource, start);
			return deferred;
		}
	}

	Key<Factory> FACTORY = Key.of(Factory.class);

	Promise<Seq<Committed<ReadBuffer, ReadBuffer>>> fetch(ResourceDescriptor resource, Seq<Revision> start);

	void resume(Listener<ReadBuffer, ReadBuffer> listener, ResourceDescriptor resource, Seq<Revision> start);

	void pause(Seq<ResourcePartition> partitions);
}