package org.ddd4j.eventstore;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ColdSource;
import org.ddd4j.infrastructure.channel.HotSource;
import org.ddd4j.infrastructure.channel.Subscriber;
import org.ddd4j.io.buffer.ReadBuffer;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Committed;
import org.ddd4j.value.versioned.Revision;
import org.reactivestreams.Subscription;

public class ChannelEventStore {

	private class ChannelSubscriber implements Subscriber {

		private ResourceDescriptor channel;
		private Subscriber delegate;
		private boolean streaming;

		@Override
		public void onSubscribe(Subscription s) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onNext(Committed<ReadBuffer, ReadBuffer> t) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onError(Throwable t) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onComplete() {
			try {
				if (streaming) {
					coldSource.read(channel, this);
				} else {
					hotSource.subscribe(channel, this);
				}
				streaming = !streaming;
			} catch (Exception e) {
				delegate.onError(e);
			}
		}

		@Override
		public Seq<Revision> loadRevisions(int[] partitions) {
			return delegate.loadRevisions(partitions);
		}

		@Override
		public void saveRevisions(Seq<Revision> revisions) {
			delegate.saveRevisions(revisions);
		}
	}

	private ColdSource coldSource;
	private HotSource hotSource;

	public void subscribe(ResourceDescriptor channel, Subscriber subscriber) throws Exception {
		coldSource.read(channel, subscriber);
	}
}
