package org.ddd4j.repository;

import org.ddd4j.Require;
import org.ddd4j.collection.Sequence;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.SchemaCodec;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.infrastructure.channel.domain.ChannelPartition;
import org.ddd4j.infrastructure.channel.domain.ChannelRevision;
import org.ddd4j.infrastructure.channel.spi.ColdSource;
import org.ddd4j.infrastructure.channel.spi.HotSource;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.value.versioned.Committed;

public class LogPublisher {

	public interface RevisionCallback {

		Promise<Sequence<ChannelRevision>> loadRevisions(Sequence<ChannelPartition> partitions);

		Promise<Void> saveRevisions(Sequence<ChannelRevision> revisions);
	}

	private class HotCallback implements HotSource.Callback {

		@Override
		public void onError(Throwable throwable) {
			// TODO Auto-generated method stub
		}

		@Override
		public void onPartitionsAssigned(Sequence<ChannelPartition> partitions) {
			callback.loadRevisions(partitions).whenCompleteSuccessfully(r -> cold.subscribe(subscriber, descriptor, r));
		}

		@Override
		public void onPartitionsRevoked(Sequence<ChannelPartition> partitions) {
			// TODO Auto-generated method stub
		}
	}

	private final ColdSource.Factory coldFactory;
	private final HotSource hotSource;
	private final ChannelPublisher publisher;

	public LogPublisher(SchemaCodec.Factory codecFactory, ColdSource.Factory coldFactory, HotSource.Factory hotFactory) {
		this.coldFactory = Require.nonNull(coldFactory);
		this.hotSource = hotFactory.createHotSource(new HotCallback(), this::onNext);
		this.publisher = new ChannelPublisher(this::onSubscribed, this::onUnsubscribed);
	}

	private void onNext(ChannelName name, Committed<ReadBuffer, ReadBuffer> committed) {
		publisher.onNext(name, committed);
	}

	private Promise<Integer> onSubscribed(ChannelName name) {
		return hotSource.subscribe(name);
	}

	private void onUnsubscribed(ChannelName name) {
		hotSource.unsubscribe(name);
	}
}