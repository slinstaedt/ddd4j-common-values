package org.ddd4j.infrastructure.channel.spi;

import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.api.RebalanceListener;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.io.ReadBuffer;
import org.ddd4j.spi.Ref;
import org.ddd4j.util.Throwing;

public interface HotSource extends Throwing.Closeable {

	interface Factory extends DataAccessFactory {

		HotSource createHotSource(CommitListener<ReadBuffer, ReadBuffer> commit, ErrorListener error, RebalanceListener rebalance);
	}

	Ref<Factory> FACTORY = Ref.of(Factory.class);

	Promise<Integer> subscribe(ChannelName name);

	void unsubscribe(ChannelName name);
}