package org.ddd4j.infrastructure.channel.spi;

import org.ddd4j.Throwing;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.RepartitioningListener;
import org.ddd4j.infrastructure.channel.api.SourceListener;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Key;

public interface HotSource extends Throwing.Closeable {

	interface Factory extends DataAccessFactory {

		HotSource createHotSource(SourceListener<ReadBuffer, ReadBuffer> source, ErrorListener error,
				RepartitioningListener repartitioning);
	}

	Key<Factory> FACTORY = Key.of(Factory.class);

	Promise<Integer> subscribe(ChannelName name);

	void unsubscribe(ChannelName name);
}