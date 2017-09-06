package org.ddd4j.infrastructure.channel.spi;

import org.ddd4j.Throwing;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.PartitionReassignmentListener;
import org.ddd4j.infrastructure.channel.api.SourceListener;
import org.ddd4j.infrastructure.channel.domain.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Key;

public interface HotSource extends Throwing.Closeable {

	interface Callback extends ErrorListener, PartitionReassignmentListener {

		default void onSubscribed(int partitionCount) {
		}
	}

	interface Factory extends DataAccessFactory {

		HotSource createHotSource(Callback callback, SourceListener<ReadBuffer, ReadBuffer> listener);
	}

	Key<Factory> FACTORY = Key.of(Factory.class);

	Promise<Integer> subscribe(ChannelName name);

	void unsubscribe(ChannelName name);
}